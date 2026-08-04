/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter

internal class EmfDoubleHistogram(
    private val namespace: String,
    private val logGroupName: String?,
    private val name: String,
    units: String?,
) : DoubleHistogram {
    private val cloudWatchUnit = mapUnitToCloudWatch(units)

    override fun record(value: Double, attributes: Attributes, context: Context?) {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = namespace,
            logGroupName = logGroupName,
            metricName = name,
            metricValue = value,
            metricUnit = cloudWatchUnit,
            attributes = attributes,
        )
        emfLog(json)
    }
}

internal class EmfLongHistogram(
    private val namespace: String,
    private val logGroupName: String?,
    private val name: String,
    units: String?,
) : LongHistogram {
    private val cloudWatchUnit = mapUnitToCloudWatch(units)

    override fun record(value: Long, attributes: Attributes, context: Context?) {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = namespace,
            logGroupName = logGroupName,
            metricName = name,
            metricValue = value.toDouble(),
            metricUnit = cloudWatchUnit,
            attributes = attributes,
        )
        emfLog(json)
    }
}

internal class EmfMonotonicCounter(
    private val namespace: String,
    private val logGroupName: String?,
    private val name: String,
    units: String?,
) : MonotonicCounter {
    private val cloudWatchUnit = mapUnitToCloudWatch(units)

    override fun add(value: Long, attributes: Attributes, context: Context?) {
        if (value < 0) return
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = namespace,
            logGroupName = logGroupName,
            metricName = name,
            metricValue = value.toDouble(),
            metricUnit = cloudWatchUnit,
            attributes = attributes,
        )
        emfLog(json)
    }
}

internal class EmfUpDownCounter(
    private val namespace: String,
    private val logGroupName: String?,
    private val name: String,
    units: String?,
) : UpDownCounter {
    private val cloudWatchUnit = mapUnitToCloudWatch(units)

    override fun add(value: Long, attributes: Attributes, context: Context?) {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = namespace,
            logGroupName = logGroupName,
            metricName = name,
            metricValue = value.toDouble(),
            metricUnit = cloudWatchUnit,
            attributes = attributes,
        )
        emfLog(json)
    }
}
