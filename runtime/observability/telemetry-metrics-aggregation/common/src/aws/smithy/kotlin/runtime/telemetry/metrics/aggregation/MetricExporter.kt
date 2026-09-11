/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

/**
 * Receives aggregated metrics and delivers them to a backend. Implementing this one interface is
 * enough for a new backend; aggregation, cardinality guarding, temporality, and scheduling are
 * inherited.
 *
 * Implementations must not throw - a telemetry failure must never surface inside a user's
 * `s3.getObject()` call - and must not block indefinitely, since [export] runs on the reader's
 * collection coroutine. The reader does no batching, so chunk against backend limits internally.
 * [export] is never called concurrently with itself.
 *
 * Install one exporter into one reader. An exporter that owns a transport closes it in [shutdown], so a
 * shared instance is silenced for the remaining readers as soon as the first one closes.
 */
public interface MetricExporter {
    /**
     * Deliver one collection cycle's worth of metrics.
     *
     * @param metrics one entry per instrument that recorded at least one measurement this cycle.
     *   Idle instruments are omitted rather than exported as zero, so charts show gaps rather than
     *   zeros for idle periods.
     */
    public suspend fun export(metrics: List<MetricData>)

    /**
     * Release resources held by this exporter. Called after the reader's final flush, so closing a
     * transport here loses nothing. Must be idempotent - reachable from a provider's `close()` and
     * from a caller holding the exporter directly.
     */
    public suspend fun shutdown()

    public companion object {
        /**
         * An exporter that discards everything. The default in builders, so a misconfigured provider
         * degrades to "no metrics" rather than throwing at construction.
         */
        public val None: MetricExporter = object : MetricExporter {
            override suspend fun export(metrics: List<MetricData>) {}
            override suspend fun shutdown() {}
        }
    }
}
