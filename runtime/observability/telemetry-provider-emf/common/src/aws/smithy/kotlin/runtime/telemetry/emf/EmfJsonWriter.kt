/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.serde.json.JsonStreamWriter
import aws.smithy.kotlin.runtime.serde.json.jsonStreamWriter
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.epochMilliseconds

internal object EmfJsonWriter {

    /**
     * Builds a single EMF JSON document for one metric recording.
     */
    fun buildEmfDocument(
        namespace: String,
        logGroupName: String?,
        metricName: String,
        metricValue: Double,
        metricUnit: CloudWatchUnit,
        attributes: Attributes,
    ): String {
        val dimensionKeys = attributes.keys.take(EmfConstants.MAX_DIMENSIONS_PER_SET)

        val writer = jsonStreamWriter()
        writer.withObject {
            writeMetadata(logGroupName, namespace, metricName, metricUnit, dimensionKeys)
            writeDimensionValues(attributes, dimensionKeys)
            writeEntry(metricName, metricValue)
        }

        return writer.bytes!!.decodeToString()
    }

    /**
     * Writes the `_aws` metadata node, which tells CloudWatch how to extract metrics from the
     * remainder of the document.
     */
    private fun JsonStreamWriter.writeMetadata(
        logGroupName: String?,
        namespace: String,
        metricName: String,
        metricUnit: CloudWatchUnit,
        dimensionKeys: List<AttributeKey<*>>,
    ) = withObject(EmfConstants.AWS_METADATA_KEY) {
        writeEntry(EmfConstants.TIMESTAMP_KEY, Instant.now().epochMilliseconds)
        writeEntryIfNotNull(EmfConstants.LOG_GROUP_NAME_KEY, logGroupName)

        withArray(EmfConstants.CLOUDWATCH_METRICS_KEY) {
            withObject {
                writeEntry(EmfConstants.NAMESPACE_KEY, namespace)
                writeDimensionKeys(dimensionKeys)
                writeMetricDefinition(metricName, metricUnit)
            }
        }
    }

    /**
     * Writes the `Dimensions` node, a list of dimension sets. A single set containing every
     * dimension is emitted.
     */
    private fun JsonStreamWriter.writeDimensionKeys(dimensionKeys: List<AttributeKey<*>>) = withArray(EmfConstants.DIMENSIONS_KEY) {
        withArray {
            dimensionKeys.forEach { writeValue(it.name) }
        }
    }

    /**
     * Writes the `Metrics` node, declaring the name and unit of the single metric in this document.
     */
    private fun JsonStreamWriter.writeMetricDefinition(metricName: String, metricUnit: CloudWatchUnit) = withArray(EmfConstants.METRICS_KEY) {
        withObject {
            writeEntry(EmfConstants.METRIC_NAME_KEY, metricName)
            writeEntry(EmfConstants.METRIC_UNIT_KEY, metricUnit.value)
        }
    }

    /**
     * Writes the target members referenced by the dimension keys declared in [writeDimensionKeys].
     */
    private fun JsonStreamWriter.writeDimensionValues(attributes: Attributes, dimensionKeys: List<AttributeKey<*>>) {
        dimensionKeys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val attributeKey = key as? AttributeKey<Any> ?: return@forEach
            writeEntry(key.name, attributes.getOrNull(attributeKey).toString())
        }
    }
}
