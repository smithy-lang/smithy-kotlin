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
 *
 * `getOrCreateMeter` honours its `scope` argument and caches one [Meter] per scope, so instrumentation
 * scope survives to the exporter. This is worth stating because it is easy to get wrong: an
 * implementation that ignores `scope` still satisfies the interface and still publishes plausible
 * metrics, it just loses the ability to attribute them.
 *
 * @param registry supplied rather than constructed so that a caller — in practice
 *   [SdkTelemetryProvider] — can hold the same registry the reader collects from. The constructor is
 *   internal because [AggregationConfig] resolution belongs to the provider builder, not here.
 */
public class SdkMeterProvider internal constructor(
    internal val registry: InstrumentRegistry,
) : MeterProvider {
    private val lock = reentrantLock()
    private val meters = mutableMapOf<String, Meter>()

    /**
     * Get or create the [Meter] for [scope].
     *
     * Cached per scope so repeated calls return the same instance. The cache is unbounded, which is safe
     * because scopes come from instrumentation code — library and client names — and are therefore drawn
     * from a small fixed set. Attribute values, which are *not* bounded that way, are handled by
     * [CardinalityGuard] instead.
     */
    override fun getOrCreateMeter(scope: String): Meter = lock.withLock { meters.getOrPut(scope) { SdkMeter(scope, registry) } }

    /**
     * Drain every instrument. Internal because collection is the reader's job: exposing it publicly would
     * let a caller reset the aggregators out from under the reader and lose an interval.
     */
    internal fun collect(): List<MetricData> = registry.collectAll()

    public companion object {
        /**
         * Convenience constructor that builds the registry from configuration.
         *
         * `internal operator invoke` rather than a second constructor: a public secondary constructor
         * taking [AggregationConfig] would expose [InstrumentRegistry] indirectly through overload
         * resolution and enlarge the API surface this module has to keep stable.
         */
        internal operator fun invoke(config: AggregationConfig, logger: Logger): SdkMeterProvider = SdkMeterProvider(InstrumentRegistry(config, logger))
    }
}
