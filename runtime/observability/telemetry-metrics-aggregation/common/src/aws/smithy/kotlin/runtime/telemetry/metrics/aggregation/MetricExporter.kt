/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

/**
 * Receives aggregated metrics and delivers them to a backend.
 *
 * This is the module's extension point and the reason the pipeline is worth sharing: a new backend
 * implements this one interface and inherits aggregation, cardinality guarding, temporality
 * handling, and scheduling.
 *
 * ### Implementation contract
 *
 * - **Do not throw.** A telemetry failure must never surface inside a user's `s3.getObject()`
 *   call. Catch, log, and drop. [PeriodicMetricReader] wraps calls defensively as well, but that
 *   is a backstop for buggy exporters, not a licence to propagate.
 * - **Do not block indefinitely.** [export] runs on the reader's collection coroutine; a hung
 *   export delays every subsequent cycle. Apply your own timeout.
 * - **Expect to be called with a large list.** Chunk against backend limits internally; the
 *   reader does no batching, because batch shape is backend-specific.
 * - **[export] is never called concurrently with itself** — the reader serializes cycles — so
 *   implementations need no internal locking for that case.
 */
public interface MetricExporter {
    /**
     * Deliver one collection cycle's worth of metrics.
     *
     * @param metrics one entry per instrument that recorded at least one measurement this cycle.
     *   Instruments with no activity are omitted rather than exported as zero, which keeps backend
     *   cost proportional to actual traffic; the consequence is gaps rather than zeros on charts for
     *   idle periods.
     */
    public suspend fun export(metrics: List<MetricData>)

    /**
     * Called once, synchronously, when this exporter is installed into a reader — before any
     * [export].
     *
     * Exists so an exporter can **reject being shared**. Nothing in the type system prevents one
     * instance from being handed to two providers, and for most backends that is a silent
     * misconfiguration rather than an error: two collection loops interleave into one destination,
     * and delta sums from independent aggregators are added together as if they came from one. An
     * exporter that cannot support this should throw here, where the stack trace still points at
     * the caller's configuration code.
     *
     * Default is a no-op: a genuinely stateless exporter is free to be shared.
     */
    public fun onAttach() {}

    /**
     * Release resources held by this exporter.
     *
     * Called exactly once, and only *after* the reader's final flush, so an implementation may
     * safely close its transport here without losing the last interval. Implementations must make
     * this **idempotent** anyway — it is reachable both from a provider's `close()` and from a
     * caller who holds the exporter directly.
     */
    public suspend fun shutdown()

    public companion object {
        /**
         * An exporter that discards everything.
         *
         * Used as the default in builders so that a misconfigured provider degrades to "no metrics"
         * instead of throwing at construction time — the same rationale as `TelemetryProvider.None`
         * in `telemetry-api`. Also convenient in tests that exercise aggregation without asserting
         * on export.
         */
        public val None: MetricExporter = object : MetricExporter {
            override suspend fun export(metrics: List<MetricData>) {}
            override suspend fun shutdown() {}
        }
    }
}
