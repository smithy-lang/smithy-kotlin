/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

/**
 * Test exporter that remembers what it was asked to do. Counts rather than flags, because the properties
 * under test are "exactly once" - a double flush or double shutdown is the failure mode.
 *
 * @param failOnExport when true, [export] throws.
 */
internal class RecordingMetricExporter(
    private val failOnExport: Boolean = false,
) : MetricExporter {
    val batches = mutableListOf<List<MetricData>>()
    var shutdownCount = 0
        private set

    override suspend fun export(metrics: List<MetricData>) {
        batches.add(metrics)
        if (failOnExport) error("export failed")
    }

    override suspend fun shutdown() {
        shutdownCount++
    }
}
