/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * [MeterProvider] that aggregates measurements in memory for a [PeriodicMetricReader] to collect.
 * `getOrCreateMeter` honours its `scope` argument and caches one [Meter] per scope, so instrumentation scope
 * survives to the exporter.
 *
 * @param registry supplied rather than constructed so [AggregatingTelemetryProvider] can hold the same registry the
 *   reader collects from. Internal because [AggregationConfig] resolution belongs to the provider builder.
 */
public class AggregatingMeterProvider internal constructor(
    internal val registry: InstrumentRegistry,
) : MeterProvider {
    private val lock = reentrantLock()
    private val meters = mutableMapOf<String, Meter>()

    /**
     * Get or create the [Meter] for [scope]. The cache is unbounded, which is safe because scopes are library
     * and client names drawn from a small fixed set; attribute values, which are not, are bounded by
     * [CardinalityGuard].
     */
    override fun getOrCreateMeter(scope: String): Meter = lock.withLock { meters.getOrPut(scope) { AggregatingMeter(scope, registry) } }

    /**
     * Drain every instrument. Internal because collection is the reader's job: exposing it publicly would
     * let a caller reset the aggregators out from under the reader and lose an interval.
     */
    internal fun collect(): List<MetricData> = registry.collectAll()

    public companion object {
        /**
         * Builds the registry from configuration. An `internal operator invoke` rather than a secondary
         * constructor, which would expose [InstrumentRegistry] through overload resolution.
         */
        internal operator fun invoke(config: AggregationConfig, logger: Logger): AggregatingMeterProvider = AggregatingMeterProvider(InstrumentRegistry(config, logger))
    }
}
