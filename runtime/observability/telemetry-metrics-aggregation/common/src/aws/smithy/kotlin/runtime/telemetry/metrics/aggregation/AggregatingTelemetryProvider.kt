/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.getLogger
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.coroutines.runBlocking

/**
 * A [TelemetryProvider] that aggregates metrics in process and exports them periodically.
 *
 * `TelemetryProvider` has no lifecycle member, so this type is additively [Closeable].
 *
 * Ownership follows creation, per the SDK-wide managed-resource convention: a provider the caller
 * constructs is the caller's to close, which is what makes one provider safe to share across clients. A
 * provider the SDK builds on the caller's behalf is wrapped for reference counting and closed by the last
 * client using it.
 *
 * Metrics only. `tracerProvider` and `contextManager` are `None`; claiming to provide tracing would
 * silently discard spans. Other backends plug in at [MetricExporter], or at [MetricReader] to also control
 * when collection happens.
 *
 * @see aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.internal.manage
 *
 * ```kotlin
 * AggregatingTelemetryProvider {
 *     exporter = CloudWatchMetricExporter { namespace = "MyApp" }
 * }.use { telemetry ->
 *     S3Client { telemetryProvider = telemetry }.use { s3 -> /* ... */ }
 * }
 * ```
 */
public class AggregatingTelemetryProvider private constructor(
    builder: Builder,
) : TelemetryProvider,
    Closeable {
    // Forwarding the caller's provider means the pipeline's own diagnostics -- cardinality overflow, export
    // failures, dropped datums -- land wherever the application's logs already go.
    override val loggerProvider: LoggerProvider = builder.loggerProvider

    private val logger: Logger = loggerProvider.getLogger<AggregatingTelemetryProvider>()

    private val aggregating = AggregatingMeterProvider(
        AggregationConfig(
            dimensions = builder.dimensions,
            detailedMetrics = builder.detailedMetrics,
            maxCardinality = builder.maxCardinality,
        ),
        logger,
    )

    override val meterProvider: MeterProvider = aggregating
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None

    private val reader: MetricReader = builder.resolveReader()

    init {
        // Eagerly, not on first record: a lazily-started reader would never publish for an application
        // whose instruments are created by code paths that happen not to run.
        @OptIn(InternalApi::class)
        reader.install(aggregating, logger)
    }

    /**
     * Collect and export now. Call this at the end of each AWS Lambda invocation - under
     * [FlushMode.OnDemand] nothing is published until it is. Harmless in periodic mode.
     */
    public suspend fun flush(): Unit = reader.flush()

    /**
     * Final flush, then release. Idempotent; recording after close is a no-op rather than a throw.
     *
     * `runBlocking` because `Closeable.close()` does not suspend and the final flush must complete or the
     * last interval is lost. The blocking window is one export.
     */
    override fun close() {
        runBlocking { reader.close() }
    }

    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): AggregatingTelemetryProvider = AggregatingTelemetryProvider(Builder().apply(block))
    }

    public class Builder {
        /**
         * When metrics are collected and where they go. Set this for more than one destination or interval;
         * for a single destination prefer [exporter]. The two are mutually exclusive.
         */
        public var metricReader: MetricReader? = null

        /**
         * Shorthand for a single destination on the default interval - equivalent to
         * `metricReader = PeriodicMetricReader { exporter = ... }`, with [flushMode] applied if set. Setting
         * this and [metricReader] fails at construction rather than leaving one exporter silently inert.
         */
        public var exporter: MetricExporter? = null

        /**
         * Overrides the platform-derived flush mode of the reader built from [exporter]; rejected alongside
         * [metricReader], which carries its own. Nullable so "not configured" stays distinguishable from
         * "explicitly periodic" - only the former is overridden by Lambda detection.
         */
        public var flushMode: FlushMode? = null

        /**
         * Attribute names promoted to backend dimensions.
         *
         * Defaults to service and operation name, matching the AWS SDK for Java v2. Both are bounded by what
         * the application was compiled against, and without them every service and operation collapses into
         * one series per instrument.
         *
         * Each name added multiplies the number of billable metrics, so prefer attributes whose values are
         * fixed at compile time; anything from a remote response (error codes, endpoints) is unbounded in
         * principle, which is why [maxCardinality] exists.
         *
         * String literals because this module does not depend on `http-client`, where the interceptor that
         * populates them lives.
         */
        public var dimensions: Set<String> = setOf("rpc.service", "rpc.method")

        /**
         * Instruments that publish full distributions instead of summary statistics. Populate only for
         * instruments whose percentiles are actually read - see [DistributionAggregator] for the cost.
         */
        public var detailedMetrics: Set<String> = emptySet()

        /**
         * Distinct attribute sets per instrument before overflow bucketing. Raising this to accommodate an
         * unbounded dimension converts a capped cost into an uncapped one; fix the dimension instead.
         */
        public var maxCardinality: Int = 1_000

        /**
         * Destination for the pipeline's own diagnostics - cardinality overflow, export failures, dropped
         * datums. Silenced by default; setting a real provider is the first step in troubleshooting missing
         * metrics.
         */
        public var loggerProvider: LoggerProvider = LoggerProvider.None

        /** Test seam for simulating the Lambda environment. */
        internal var platform: PlatformProvider = PlatformProvider.System

        /**
         * Reconcile [metricReader] and the [exporter] shorthand into the one reader the provider drives.
         * Neither set is not an error - it yields a reader over [MetricExporter.None], so misconfigured
         * telemetry cannot stop an application booting.
         */
        internal fun resolveReader(): MetricReader {
            val reader = metricReader
            if (reader != null) {
                require(exporter == null) {
                    "Set either metricReader or exporter, not both. The exporter shorthand builds a " +
                        "PeriodicMetricReader; configure the exporter on your reader instead."
                }
                require(flushMode == null) {
                    "flushMode configures the reader built by the exporter shorthand. With an explicit " +
                        "metricReader, set it on that reader."
                }
                return reader
            }

            return PeriodicMetricReader {
                exporter = this@Builder.exporter ?: MetricExporter.None
                flushMode = this@Builder.flushMode
                platform = this@Builder.platform
            }
        }
    }
}
