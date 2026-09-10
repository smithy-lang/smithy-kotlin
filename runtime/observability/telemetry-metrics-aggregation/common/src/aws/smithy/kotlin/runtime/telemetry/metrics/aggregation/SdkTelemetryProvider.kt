/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider
import kotlinx.coroutines.runBlocking

/**
 * A [TelemetryProvider] that aggregates metrics in process and exports them periodically.
 *
 * `TelemetryProvider` has no lifecycle member, so this type is additively [Closeable].
 *
 * **Ownership follows creation**, per the SDK-wide managed-resource convention: a provider the caller
 * constructs and assigns is the caller's to close, and closing a client does not close it. That is what
 * makes one provider safe to share across several clients — if a client closed it, the first client to shut
 * down would silently stop metrics for the rest. A provider the SDK ever builds on the caller's behalf is
 * instead wrapped for reference counting and closed by the last client using it.
 *
 * @see aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.internal.manage
 *
 * ```kotlin
 * SdkTelemetryProvider {
 *     exporter = CloudWatchMetricExporter { namespace = "MyApp" }
 * }.use { telemetry ->
 *     S3Client { telemetryProvider = telemetry }.use { s3 -> /* ... */ }
 * }
 * ```
 */
public class SdkTelemetryProvider private constructor(
    builder: Builder,
) : AggregatingTelemetryProvider(
    config = AggregationConfig(
        dimensions = builder.dimensions,
        detailedMetrics = builder.detailedMetrics,
        maxCardinality = builder.maxCardinality,
    ),
    // Passed straight through: forwarding the caller's provider means the pipeline's own diagnostics land
    // wherever the application's logs already go.
    loggerProvider = builder.loggerProvider,
    // Fully qualified, because SLF4J and log4j2 treat logger names as a dot-delimited hierarchy: a bare
    // "SdkTelemetryProvider" could not be matched by a level set on this package. Spelled out as a literal
    // rather than derived from the class, since a super-constructor argument cannot reference the class
    // being constructed.
    loggerName = "aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.SdkTelemetryProvider",
) {
    // The one thing this subclass adds to the base: the collection trigger. tracerProvider and
    // contextManager are inherited as None — this module is a *metrics* pipeline, and claiming to provide
    // tracing would silently discard spans, which is the defect this module exists to avoid.
    private val reader: MetricReader = builder.resolveReader()

    init {
        // Installed here rather than lazily on first record: a lazily-started reader would never publish for
        // an application that configures metrics but whose instruments are created by code paths that
        // happen not to run.
        @OptIn(InternalApi::class)
        reader.install(aggregating, logger)
    }

    /**
     * Collect and export now.
     *
     * Call this at the end of each AWS Lambda invocation — under [FlushMode.OnDemand] nothing is published
     * until it is called. Harmless in periodic mode, where it publishes early.
     */
    override suspend fun flush(): Unit = reader.flush()

    /**
     * Final flush, then release. Idempotent; recording after close is a no-op rather than a throw.
     *
     * `runBlocking` is regrettable but forced: `Closeable.close()` is not a suspend function, and the final
     * flush must complete before the process exits or the last interval is lost. The blocking window is one
     * export. A `suspend fun closeSuspend()` alternative was considered and rejected — it would leave
     * `close()` either lying or unimplemented, and `use { }` would not work.
     */
    override fun close() {
        runBlocking { reader.close() }
    }

    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): SdkTelemetryProvider = SdkTelemetryProvider(Builder().apply(block))
    }

    public class Builder {
        /**
         * When metrics are collected and where they go. The exporter is configured on a reader rather than
         * here so that the collection interval, the temporality it implies, and the exporter they apply to
         * stay in one object.
         *
         * Set this when more than one destination or more than one interval is needed. For the single
         * destination case prefer [exporter], which builds a [PeriodicMetricReader] with default settings —
         * the two are mutually exclusive.
         */
        public var metricReader: MetricReader? = null

        /**
         * Shorthand for a single destination on the default interval: equivalent to
         * `metricReader = PeriodicMetricReader { exporter = ... }`, with [flushMode] applied to that reader
         * if set.
         *
         * Exists so the common case costs two concepts rather than three. Mutually exclusive with
         * [metricReader]; setting both fails at construction rather than silently preferring one, because
         * either choice of precedence would leave one configured exporter publishing nothing.
         */
        public var exporter: MetricExporter? = null

        /**
         * Overrides the platform-derived flush mode of the reader built from [exporter].
         *
         * Ignored — and rejected — when [metricReader] is set, since a caller-built reader carries its own.
         * Nullable so that "not configured" stays distinguishable from "explicitly configured as periodic";
         * only the former is overridden by Lambda detection.
         */
        public var flushMode: FlushMode? = null

        /**
         * Attribute names promoted to backend dimensions.
         *
         * Defaults to the service and operation names, matching the AWS SDK for Java v2's default dimension
         * set. Both are bounded by what the application was compiled against, so the default cannot cause a
         * cardinality explosion, and without them every service and operation would collapse into one
         * series per instrument — which is rarely the breakdown anyone wants.
         *
         * Set to [emptySet] for a single series per instrument, or add names to break down further. Each
         * name added multiplies the number of billable metrics, so prefer attributes whose value set is
         * fixed at compile time; anything influenced by a remote response (error codes, endpoints) is
         * unbounded in principle and is why [maxCardinality] exists.
         *
         * String literals rather than constants because this module deliberately does not depend on
         * `http-client`, which is where the interceptor that populates them lives.
         */
        public var dimensions: Set<String> = setOf("rpc.service", "rpc.method")

        /**
         * Instruments that publish full distributions instead of summary statistics.
         *
         * Empty by default. Populate it only for instruments whose percentiles are actually read — see
         * [DistributionAggregator] for the memory cost.
         */
        public var detailedMetrics: Set<String> = emptySet()

        /**
         * Distinct attribute sets per instrument before overflow bucketing.
         *
         * Raise this only after confirming the dimensions in use have genuinely bounded values. Raising it
         * to accommodate an unbounded dimension converts a capped cost into an uncapped one — fix the
         * dimension instead.
         */
        public var maxCardinality: Int = 1_000

        /**
         * Destination for the pipeline's own diagnostics — cardinality overflow, export failures, dropped
         * datums. Defaults to [LoggerProvider.None], which silences them; setting a real provider is the
         * first step in troubleshooting missing metrics.
         */
        public var loggerProvider: LoggerProvider = LoggerProvider.None

        /** Internal seam for tests to simulate the Lambda environment. Not part of the public API. */
        internal var platform: PlatformProvider = PlatformProvider.System

        /**
         * Reconcile [metricReader] and the [exporter] shorthand into the one reader the provider drives.
         *
         * Fails on the ambiguous combination rather than picking a winner: with both set, either precedence
         * rule leaves a configured exporter publishing nothing, and a silently inert exporter is the hardest
         * telemetry defect to notice. Neither set is *not* an error — it yields a reader over
         * [MetricExporter.None], preserving the "misconfigured telemetry must not stop an application
         * booting" property that the exporter defaults exist for.
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
