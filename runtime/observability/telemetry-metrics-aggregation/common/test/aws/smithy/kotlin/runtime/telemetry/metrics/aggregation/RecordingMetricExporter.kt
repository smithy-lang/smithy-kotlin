/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

/**
 * Test exporter that remembers what it was asked to do.
 *
 * Counts rather than flags, because several of the properties under test are "exactly once" rather than
 * "at least once" — a double flush or a double shutdown is the failure mode being guarded against.
 *
 * @param failOnExport when true, [export] throws. Used to check the reader treats a broken exporter as a
 *   logged failure rather than letting it kill the collection loop.
 */
internal class RecordingMetricExporter(
    private val failOnExport: Boolean = false,
) : MetricExporter {
    val batches = mutableListOf<List<MetricData>>()
    var attachCount = 0
        private set
    var shutdownCount = 0
        private set

    override fun onAttach() {
        attachCount++
    }

    override suspend fun export(metrics: List<MetricData>) {
        batches.add(metrics)
        if (failOnExport) error("export failed")
    }

    override suspend fun shutdown() {
        shutdownCount++
    }
}
