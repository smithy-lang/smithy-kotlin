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
 * Determines when collection happens.
 *
 * A sealed interface rather than a `Duration?` where null means on-demand: the two modes differ in
 * behaviour, not just in timing, and `null` would leave a reader that never publishes looking like a
 * misconfiguration rather than a deliberate choice.
 */
public sealed interface FlushMode {
    /**
     * Collect on a timer.
     *
     * @param interval one minute by default, matching the AWS SDK for Java v2's upload frequency and
     *   CloudWatch's standard-resolution period. Shorter intervals do not improve granularity at
     *   `storageResolution = 60` — CloudWatch aggregates into 60-second periods regardless — so lowering
     *   this mostly buys extra `PutMetricData` calls and cost.
     */
    public data class Periodic(public val interval: Duration = 1.minutes) : FlushMode

    /**
     * Collect only when [PeriodicMetricReader.flush] is called explicitly.
     *
     * The default under AWS Lambda, where the execution environment is frozen between invocations: a
     * `delay()` scheduled during one invocation may not resume until the next, or ever, so timer-driven
     * publishing loses whatever is buffered when the freeze happens.
     *
     * Trade-off: gauge resolution equals flush frequency, because async callbacks are sampled only during
     * collection. A gauge in this mode reports its value at each invocation boundary, not continuously.
     */
    public data object OnDemand : FlushMode
}

/**
 * Drives collection cycles and hands the results to a [MetricExporter].
 *
 * ### Scope ownership — why this class owns a `CoroutineScope` at all
 *
 * `TelemetryProvider` declares no lifecycle member: no `close()`, no `shutdown()`, and it does not extend
 * `Closeable`. It is four read-only properties. Nothing in the telemetry contract will ever tell this
 * reader to stop, and there is no ambient scope to inherit. So the reader creates and owns one, and every
 * part of that construction is a deliberate choice:
 *
 * - **`SupervisorJob`** — with a regular `Job`, one failed child cancels the parent scope, and collection
 *   would end permanently after a single bad cycle. The failure would be silent, which is the worst
 *   property a telemetry component can have.
 * - **`Dispatchers.Default`** — daemon-backed on the JVM, so the worst case of a forgotten [close] is a
 *   leak until process exit rather than a process that refuses to exit. A bespoke thread pool with
 *   non-daemon threads would turn a missing `close()` into a hung shutdown, which users would rightly
 *   report as a bug in the SDK.
 *
 * The residual weakness is acknowledged rather than hidden: a caller who never closes the provider leaks
 * one coroutine. That cost is absorbed here deliberately: the alternative is a lifecycle member on the
 * shared telemetry interfaces, which have none and are used by providers that need none.
 *
 * ### Construction
 *
 * Built by the caller and assigned to [SdkTelemetryProvider.Builder.metricReader], or created implicitly
 * by that builder's [exporter][SdkTelemetryProvider.Builder.exporter] shorthand for the single-destination
 * case:
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

    // A coroutine Mutex, not a lock: it suspends rather than blocking a dispatcher thread, and `export`
    // is a suspend function so a blocking lock could not be held across it anyway. Serializes
    // collect+export so a user's flush() cannot interleave with a timer tick — two concurrent cycles
    // would each drain half the aggregators and publish two partial batches.
    private val exportMutex = Mutex()

    // Atomic because close() may be called from any thread while the timer runs on another, and
    // compareAndSet is what makes close() idempotent without a lock.
    private val closed = atomic(false)

    private var timer: Job? = null

    // `lateinit` because of a genuine circularity: the provider needs the reader it collects with, and the
    // reader needs the provider to collect from. Resolved by two-phase init — construct, then
    // install(provider, logger) — rather than by passing a factory lambda, which would make the ordering
    // harder to see. Only ever assigned from install().
    private lateinit var provider: SdkMeterProvider

    // Also deferred to install(): the logger comes from the provider's LoggerProvider, which the caller
    // configures on the provider builder, not here. A reader constructed standalone therefore cannot have
    // one yet.
    private lateinit var logger: Logger

    /**
     * Bind the provider and logger and, in [FlushMode.Periodic], start the timer.
     *
     * Called once by [SdkTelemetryProvider]'s initializer. Calling it twice would start a second timer and
     * double-publish, which is why [MetricReader] keeps it off the supported surface.
     */
    @InternalApi
    override fun install(provider: SdkMeterProvider, logger: Logger) {
        // Before anything else: let the exporter refuse to be shared. Done here rather than in
        // Builder.build() because a Builder is single-use, so the second attachment of a *reused exporter*
        // — or of a reused reader — happens in a second, otherwise-innocent-looking builder. Throwing
        // during construction is the one place in this design where a telemetry misconfiguration is fatal,
        // and it is deliberate: the alternative is two collection loops silently corrupting each other's
        // sums, which no amount of logging makes debuggable.
        exporter.onAttach()

        this.provider = provider
        this.logger = logger
        val mode = flushMode ?: defaultFlushMode(platform)
        if (mode is FlushMode.Periodic) {
            timer = scope.launch {
                while (isActive) {
                    // delay() before the first collect, not after: an immediate collect at startup would
                    // publish a near-empty interval and, worse, would race application code that has not
                    // finished registering its gauges.
                    delay(mode.interval)
                    collectAndExport()
                }
            }
        }
        // In OnDemand mode no coroutine is launched at all — nothing to leak, and nothing that can be
        // caught mid-cycle by a Lambda freeze.
    }

    /**
     * Collect and export immediately.
     *
     * Safe to call concurrently and safe to call after [close] — a closed reader returns without publishing
     * rather than throwing, because a flush racing shutdown is a normal outcome of ordinary teardown and
     * should not surface as an error in application code.
     */
    override suspend fun flush() {
        if (closed.value) return
        collectAndExport()
    }

    /**
     * One collection cycle. The single place where collection and export are sequenced, so the error
     * handling below applies uniformly to the timer path and the [flush] path.
     */
    private suspend fun collectAndExport() = exportMutex.withLock {
        val metrics = try {
            provider.collect()
        } catch (e: CancellationException) {
            // Rethrown, never swallowed. Cancellation is a control-flow signal — from close() or from scope
            // teardown — and catching it would make this coroutine uncancellable, turning a clean shutdown
            // into a hang.
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "metric collection failed; skipping interval" }
            // Returning rather than rethrowing keeps the timer loop alive for the next cycle.
            return@withLock
        }

        // Skip the export entirely when nothing was recorded. Avoids a pointless publish call — and its
        // cost — during idle periods.
        if (metrics.isEmpty()) return@withLock

        try {
            exporter.export(metrics)
            // Logged at DEBUG on the success path so that "is the pipeline running at all?" is answerable
            // without a backend round-trip. The warnings below only fire on failure, which leaves the
            // silent-but-healthy case indistinguishable from a reader that never ticks.
            logger.debug { "collected and exported ${metrics.size} instrument(s)" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Exporters are contractually non-throwing, so reaching here means a buggy exporter. Guarded
            // anyway: an uncaught throw would kill this cycle and, without the SupervisorJob above, could
            // take the whole scope with it.
            logger.warn(e) { "metric export failed; dropped ${metrics.size} instrument(s)" }
        }
    }

    /**
     * Stop the timer, perform a final flush, then release the exporter and the scope.
     *
     * Idempotent: a second call returns immediately.
     */
    override suspend fun close() {
        // compareAndSet, not a plain read-then-write: two concurrent close() calls must not both proceed to
        // flush and shutdown, which would double-export and double-shutdown.
        if (!closed.compareAndSet(expect = false, update = true)) return

        // Cancel the timer first so it cannot start a cycle that races the final flush.
        timer?.cancel()
        try {
            // Ordering is load-bearing: flush BEFORE exporter.shutdown(). Reversed, the last interval —
            // often the most interesting one, since it contains whatever happened just before shutdown —
            // would be collected and then handed to a closed exporter.
            collectAndExport()
        } finally {
            // In `finally` so a failing final flush still releases the exporter's resources. runCatching
            // because shutdown() is the last chance to release anything; letting it throw here would skip
            // scope.cancel() and leak the coroutine we are trying to stop.
            runCatching { exporter.shutdown() }
                .onFailure { logger.warn(it) { "exporter shutdown failed" } }
            scope.cancel()
        }
    }

    public companion object {
        /**
         * Set by the Lambda runtime for every function. Chosen over `AWS_EXECUTION_ENV` because that
         * variable is absent in some container-based Lambda deployments, and over `AWS_LAMBDA_RUNTIME_API`
         * because that one is specific to custom runtimes.
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
         * Pick a flush mode from the environment. Lives here rather than on the provider because the timer
         * this decides about is owned here.
         *
         * Lambda freezes the execution environment between invocations, so a timer scheduled during one
         * invocation may not resume until the next — or at all, if the environment is reclaimed — losing
         * whatever is buffered. Defaulting to on-demand there makes the common case correct without
         * configuration; the cost is that the handler must call [SdkTelemetryProvider.flush].
         *
         * @param platform injected rather than reading the environment directly so tests can exercise both
         *   branches without mutating process state.
         */
        internal fun defaultFlushMode(platform: PlatformProvider): FlushMode = if (platform.getenv(ENV_LAMBDA_FUNCTION) != null) {
            FlushMode.OnDemand
        } else {
            FlushMode.Periodic(1.minutes)
        }
    }

    public class Builder {
        /**
         * Where this reader's collected metrics go. Effectively required.
         *
         * Defaults to [MetricExporter.None] rather than being a required parameter so that a misconfigured
         * reader degrades to "collects but publishes nothing" instead of throwing during application
         * startup. Telemetry misconfiguration should not prevent an application booting.
         */
        public var exporter: MetricExporter = MetricExporter.None

        /**
         * Overrides the platform-derived default.
         *
         * Nullable rather than pre-set to [FlushMode.Periodic] so that "not configured" is distinguishable
         * from "explicitly configured as periodic" — only the former should be overridden by Lambda
         * detection.
         */
        public var flushMode: FlushMode? = null

        /** Internal seam for tests to simulate the Lambda environment. Not part of the public API. */
        internal var platform: PlatformProvider = PlatformProvider.System
    }
}
