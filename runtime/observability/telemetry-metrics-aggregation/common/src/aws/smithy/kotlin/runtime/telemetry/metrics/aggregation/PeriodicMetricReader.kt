/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Determines when collection happens. A sealed interface rather than a nullable `Duration` because the two
 * modes differ in behaviour, not just timing.
 */
public sealed interface FlushMode {
    /**
     * Collect on a timer.
     *
     * @param interval one minute by default, matching the AWS SDK for Java v2 and CloudWatch's
     *   standard-resolution period. At `storageResolution = 60` a shorter interval buys extra
     *   `PutMetricData` calls rather than granularity.
     */
    public class Periodic(public val interval: Duration = 1.minutes) : FlushMode {
        override fun equals(other: Any?): Boolean = other is Periodic && other.interval == interval
        override fun hashCode(): Int = interval.hashCode()
        override fun toString(): String = "Periodic(interval=$interval)"
    }

    /**
     * Collect only when [PeriodicMetricReader.flush] is called explicitly. The default under AWS Lambda,
     * where the environment is frozen between invocations and a scheduled `delay()` may not resume until
     * the next one, or ever.
     *
     * Gauge resolution equals flush frequency here, since async callbacks are sampled only during
     * collection.
     */
    public data object OnDemand : FlushMode
}

/**
 * Drives collection cycles and hands the results to a [MetricExporter].
 *
 * `TelemetryProvider` has no lifecycle member and there is no ambient scope to inherit, so the reader owns
 * its own: a `SupervisorJob`, so one bad cycle cannot silently end collection for the process lifetime, on
 * `Dispatchers.Default`, which is daemon-backed on the JVM so a forgotten [close] leaks one coroutine
 * rather than hanging shutdown.
 *
 * Built by the caller and assigned to [AggregatingTelemetryProvider.Builder.metricReader], or created implicitly by
 * that builder's [exporter][AggregatingTelemetryProvider.Builder.exporter] shorthand:
 *
 * ```kotlin
 * PeriodicMetricReader(interval = 30.seconds) {
 *     exporter = CloudWatchMetricExporter { namespace = "MyApp" }
 * }
 * ```
 */
public class PeriodicMetricReader private constructor(builder: Builder) : MetricReader {
    private val exporter: MetricExporter = builder.exporter
    private val flushMode: FlushMode? = builder.flushMode
    private val platform: PlatformProvider = builder.platform

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Suspends rather than blocking a dispatcher thread, and `export` suspends so a blocking lock could not
    // be held across it. Serializes collect+export so flush() cannot interleave with a timer tick and drain
    // half the aggregators into each of two partial batches.
    private val exportMutex = Mutex()

    // close() may be called from any thread while the timer runs on another; compareAndSet is what makes it
    // idempotent without a lock.
    private val closed = atomic(false)

    private var timer: Job? = null

    // `lateinit` for a genuine circularity: the provider needs the reader it collects with, and the reader
    // needs the provider to collect from. Both are assigned only from install().
    private lateinit var provider: AggregatingMeterProvider
    private lateinit var logger: Logger

    /**
     * Bind the provider and logger and, in [FlushMode.Periodic], start the timer.
     *
     * Called once by [AggregatingTelemetryProvider]'s initializer. A second call would start a second timer and
     * double-publish, which is why [MetricReader] keeps this off the supported surface.
     */
    @InternalApi
    override fun install(provider: AggregatingMeterProvider, logger: Logger) {
        this.provider = provider
        this.logger = logger
        val mode = flushMode ?: defaultFlushMode(platform)
        if (mode is FlushMode.Periodic) {
            timer = scope.launch {
                while (isActive) {
                    // delay() first: an immediate collect would publish a near-empty interval and race
                    // application code that has not finished registering its gauges.
                    delay(mode.interval)
                    collectAndExport()
                }
            }
        }
        // OnDemand launches no coroutine - nothing to leak, and nothing a Lambda freeze can catch mid-cycle.
    }

    /**
     * Collect and export immediately. Safe to call concurrently, and after [close] - a flush racing shutdown
     * is ordinary teardown, so a closed reader returns without publishing rather than throwing.
     */
    override suspend fun flush() {
        if (closed.value) return
        collectAndExport()
    }

    /**
     * One collection cycle. The only place collection and export are sequenced, so this error handling
     * applies to both the timer path and [flush].
     */
    private suspend fun collectAndExport() = exportMutex.withLock {
        val metrics = try {
            provider.collect()
        } catch (e: CancellationException) {
            // Never swallowed: catching it would make this coroutine uncancellable and turn a clean shutdown
            // into a hang.
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "metric collection failed; skipping interval" }
            // Returning rather than rethrowing keeps the timer loop alive for the next cycle.
            return@withLock
        }

        // No pointless publish call, or its cost, during idle periods.
        if (metrics.isEmpty()) return@withLock

        try {
            exporter.export(metrics)
            // On the success path too, so "is the pipeline running at all?" is answerable without a backend
            // round-trip.
            logger.debug { "collected and exported ${metrics.size} instrument(s)" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Exporters are contractually non-throwing, so this means a buggy one. Guarded anyway.
            logger.warn(e) { "metric export failed; dropped ${metrics.size} instrument(s)" }
        }
    }

    /** Stop the timer, flush a final time, then release the exporter and the scope. Idempotent. */
    override suspend fun close() {
        // Two concurrent close() calls must not both flush and shut down.
        if (!closed.compareAndSet(expect = false, update = true)) return

        // Cancel the timer first so it cannot start a cycle that races the final flush.
        timer?.cancel()
        try {
            // Flush before shutdown; reversed, the last interval would be handed to a closed exporter.
            collectAndExport()
        } finally {
            // In `finally` so a failing flush still releases the exporter, and caught so a failing shutdown
            // does not skip scope.cancel() and leak the coroutine being stopped.
            runCatching { exporter.shutdown() }
                .onFailure { logger.warn(it) { "exporter shutdown failed" } }
            scope.cancel()
        }
    }

    public companion object {
        /**
         * Set by the Lambda runtime for every function. Preferred over `AWS_EXECUTION_ENV`, absent in some
         * container-based deployments, and `AWS_LAMBDA_RUNTIME_API`, specific to custom runtimes.
         */
        internal const val ENV_LAMBDA_FUNCTION: String = "AWS_LAMBDA_FUNCTION_NAME"

        /**
         * @param interval convenience for the common case; equivalent to setting
         *   `flushMode = FlushMode.Periodic(interval)` in [block]. Left null to let the environment decide
         *   (see [defaultFlushMode]).
         * @param block configures the reader.
         */
        public operator fun invoke(
            interval: Duration? = null,
            block: Builder.() -> Unit,
        ): PeriodicMetricReader = PeriodicMetricReader(
            Builder()
                .apply { interval?.let { flushMode = FlushMode.Periodic(it) } }
                .apply(block),
        )

        /**
         * Pick a flush mode from the environment. Lambda freezes the environment between invocations, so a
         * scheduled timer may not resume until the next one - or at all - losing whatever is buffered.
         * Defaulting to on-demand there costs only that the handler must call [AggregatingTelemetryProvider.flush].
         *
         * @param platform injected so tests can exercise both branches without mutating process state.
         */
        internal fun defaultFlushMode(platform: PlatformProvider): FlushMode = if (platform.getenv(ENV_LAMBDA_FUNCTION) != null) {
            FlushMode.OnDemand
        } else {
            FlushMode.Periodic(1.minutes)
        }
    }

    public class Builder {
        /**
         * Where this reader's collected metrics go. Effectively required, but defaulted so a misconfigured
         * reader collects and publishes nothing rather than preventing an application booting.
         */
        public var exporter: MetricExporter = MetricExporter.None

        /**
         * Overrides the platform-derived default. Nullable so "not configured" stays distinguishable from
         * "explicitly periodic" - only the former is overridden by Lambda detection.
         */
        public var flushMode: FlushMode? = null

        /** Test seam for simulating the Lambda environment. */
        internal var platform: PlatformProvider = PlatformProvider.System
    }
}
