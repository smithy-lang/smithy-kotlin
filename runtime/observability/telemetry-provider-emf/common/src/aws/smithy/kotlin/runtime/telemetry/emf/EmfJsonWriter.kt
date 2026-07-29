/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
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
        val writer = jsonStreamWriter()

        writer.beginObject()

        writer.writeName(EmfConstants.AWS_METADATA_KEY)
        writer.beginObject()

        writer.writeName(EmfConstants.TIMESTAMP_KEY)
        writer.writeValue(Instant.now().epochMilliseconds)

        if (logGroupName != null) {
            writer.writeName(EmfConstants.LOG_GROUP_NAME_KEY)
            writer.writeValue(logGroupName)
        }

        writer.writeName(EmfConstants.CLOUDWATCH_METRICS_KEY)
        writer.beginArray()
        writer.beginObject()

        writer.writeName(EmfConstants.NAMESPACE_KEY)
        writer.writeValue(namespace)

        writer.writeName(EmfConstants.DIMENSIONS_KEY)
        writer.beginArray()
        writer.beginArray()
        val dimensionKeys = attributes.keys.take(EmfConstants.MAX_DIMENSIONS_PER_SET)
        for (key in dimensionKeys) {
            writer.writeValue(key.name)
        }
        writer.endArray()
        writer.endArray()

        writer.writeName(EmfConstants.METRICS_KEY)
        writer.beginArray()
        writer.beginObject()
        writer.writeName(EmfConstants.METRIC_NAME_KEY)
        writer.writeValue(metricName)
        writer.writeName(EmfConstants.METRIC_UNIT_KEY)
        writer.writeValue(metricUnit.value)
        writer.endObject()
        writer.endArray()

        writer.endObject()
        writer.endArray()
        writer.endObject()

        @Suppress("UNCHECKED_CAST")
        for (key in dimensionKeys) {
            val attributeKey = key as? AttributeKey<Any> ?: continue
            writer.writeName(key.name)
            writer.writeValue(attributes.getOrNull(attributeKey).toString())
        }

        writer.writeName(metricName)
        writer.writeValue(metricValue)

        writer.endObject()

        return writer.bytes!!.decodeToString()
    }
}
