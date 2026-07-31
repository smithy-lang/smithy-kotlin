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

/**
 * A single dimension to emit, with both its key and value already normalized to satisfy EMF's
 * length constraints. The EMF spec requires every key declared in the `Dimensions` array to also be
 * a member of the root node, so both sites are written from the same normalized instance to keep
 * them in agreement.
 */
private data class EmfDimension(val name: String, val value: String)

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
        val dimensions = resolveDimensions(attributes)
        val name = metricName.truncateTo(EmfConstants.MAX_METRIC_NAME_LENGTH, "metric name")

        val writer = jsonStreamWriter()
        writer.withObject {
            writeMetadata(logGroupName, namespace, name, metricUnit, dimensions)
            dimensions.forEach { writeEntry(it.name, it.value) }
            writeEntry(name, metricValue)
        }

        return writer.bytes!!.decodeToString()
    }

    /**
     * Resolves [attributes] into the dimensions to emit, enforcing EMF's dimension set constraints.
     *
     * Overflow beyond [EmfConstants.MAX_DIMENSIONS_PER_SET] is dropped with a warning naming the
     * dropped keys: dimensions define a metric's identity in CloudWatch, so discarding them silently
     * would land the metric on an unexpected series with no indication of why.
     */
    private fun resolveDimensions(attributes: Attributes): List<EmfDimension> {
        val keys = attributes.keys.toList()

        if (keys.size > EmfConstants.MAX_DIMENSIONS_PER_SET) {
            val dropped = keys.drop(EmfConstants.MAX_DIMENSIONS_PER_SET)
            emfLog(
                "[WARN] EMF dimension set contains ${keys.size} keys but at most " +
                    "${EmfConstants.MAX_DIMENSIONS_PER_SET} are allowed; dropping ${dropped.size}: " +
                    dropped.joinToString { it.name },
            )
        }

        return keys.take(EmfConstants.MAX_DIMENSIONS_PER_SET).map { key ->
            @Suppress("UNCHECKED_CAST")
            val typedKey = key as AttributeKey<Any>
            EmfDimension(
                name = key.name.truncateTo(EmfConstants.MAX_DIMENSION_KEY_LENGTH, "dimension key"),
                value = attributes
                    .getOrNull(typedKey)
                    .toString()
                    .truncateTo(EmfConstants.MAX_DIMENSION_VALUE_LENGTH, "dimension value for '${key.name}'"),
            )
        }
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
        dimensions: List<EmfDimension>,
    ) = withObject(EmfConstants.AWS_METADATA_KEY) {
        writeEntry(EmfConstants.TIMESTAMP_KEY, Instant.now().epochMilliseconds)
        writeEntryIfNotNull(EmfConstants.LOG_GROUP_NAME_KEY, logGroupName)

        withArray(EmfConstants.CLOUDWATCH_METRICS_KEY) {
            withObject {
                writeEntry(EmfConstants.NAMESPACE_KEY, namespace)
                writeDimensionKeys(dimensions)
                writeMetricDefinition(metricName, metricUnit)
            }
        }
    }

    /**
     * Writes the `Dimensions` node, a list of dimension sets. A single set containing every
     * dimension is emitted.
     */
    private fun JsonStreamWriter.writeDimensionKeys(dimensions: List<EmfDimension>) = withArray(EmfConstants.DIMENSIONS_KEY) {
        withArray {
            dimensions.forEach { writeValue(it.name) }
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
}
