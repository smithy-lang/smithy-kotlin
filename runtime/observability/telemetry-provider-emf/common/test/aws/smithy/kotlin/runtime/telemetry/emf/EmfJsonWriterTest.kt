/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmfJsonWriterTest {
    @Test
    fun producesValidEmfStructure() {
        val attributes = attributesOf {
            "rpc.service" to "S3"
            "rpc.method" to "GetObject"
        }

        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "TestNamespace",
            logGroupName = "/aws/lambda/test-fn",
            metricName = "smithy.client.call.duration",
            metricValue = 0.042,
            metricUnit = CloudWatchUnit.SECONDS,
            attributes = attributes,
        )

        assertContains(json, "\"_aws\"")
        assertContains(json, "\"Timestamp\"")
        assertContains(json, "\"LogGroupName\":\"/aws/lambda/test-fn\"")
        assertContains(json, "\"CloudWatchMetrics\"")
        assertContains(json, "\"Namespace\":\"TestNamespace\"")
        assertContains(json, "\"Dimensions\"")
        assertContains(json, "\"rpc.service\"")
        assertContains(json, "\"rpc.method\"")
        assertContains(json, "\"Name\":\"smithy.client.call.duration\"")
        assertContains(json, "\"Unit\":\"Seconds\"")
        assertContains(json, "\"smithy.client.call.duration\":0.042")
        assertContains(json, "\"rpc.service\":\"S3\"")
        assertContains(json, "\"rpc.method\":\"GetObject\"")
    }

    @Test
    fun omitsLogGroupNameWhenNull() {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.COUNT,
            attributes = emptyAttributes(),
        )

        assertFalse(json.contains("LogGroupName"))
    }

    @Test
    fun handlesEmptyAttributes() {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 42.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = emptyAttributes(),
        )

        assertContains(json, "\"Dimensions\":[[]]")
        assertContains(json, "\"test.metric\":42.0")
    }

    @Test
    fun alwaysEmitsUnit() {
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = emptyAttributes(),
        )

        assertContains(json, "\"Unit\":\"None\"")
    }

    @Test
    fun capsDimensionsAtMax() {
        val attributes = attributesOf {
            repeat(35) { i -> "dim$i" to "value$i" }
        }

        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = attributes,
        )

        // Should have at most MAX_DIMENSIONS_PER_SET dimension keys
        val dimCount = "\"dim\\d+\"".toRegex().findAll(json).count()
        // Each dimension appears twice (once in Dimensions array, once as top-level value)
        assertTrue(dimCount <= EmfConstants.MAX_DIMENSIONS_PER_SET * 2)
    }

    @Test
    fun dropsExcessDimensionsAtMax() {
        val attributes = attributesOf {
            repeat(35) { i -> "dim$i" to "value$i" }
        }

        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = attributes,
        )

        assertEquals(EmfConstants.MAX_DIMENSIONS_PER_SET, dimensionSet(json).size)
    }

    @Test
    fun truncatesLongDimensionKey() {
        val longKey = "k".repeat(300)
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = attributesOf { longKey to "v" },
        )

        val truncated = "k".repeat(EmfConstants.MAX_DIMENSION_KEY_LENGTH)
        // Truncated consistently in both the Dimensions array and as the target member
        assertEquals(listOf(truncated), dimensionSet(json))
        assertContains(json, "\"$truncated\":\"v\"")
    }

    @Test
    fun truncatesLongDimensionValue() {
        val longValue = "v".repeat(2000)
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = attributesOf { "dim" to longValue },
        )

        assertContains(json, "\"dim\":\"${"v".repeat(EmfConstants.MAX_DIMENSION_VALUE_LENGTH)}\"")
    }

    @Test
    fun truncatesLongMetricName() {
        val longName = "n".repeat(2000)
        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = longName,
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = emptyAttributes(),
        )

        val truncated = "n".repeat(EmfConstants.MAX_METRIC_NAME_LENGTH)
        // Truncated consistently in both the metric definition and the metric target member
        assertContains(json, "\"Name\":\"$truncated\"")
        assertContains(json, "\"$truncated\":1.0")
    }

    @Test
    fun dimensionKeysAreAlwaysTargetMembers() {
        // The EMF spec requires every key in a DimensionSet to also be a member of the root node.
        // Exercised with enough oversized keys to trigger both truncation and overflow dropping.
        val attributes = attributesOf {
            repeat(35) { i -> "d".repeat(300) + i to "value$i" }
        }

        val json = EmfJsonWriter.buildEmfDocument(
            namespace = "Test",
            logGroupName = null,
            metricName = "test.metric",
            metricValue = 1.0,
            metricUnit = CloudWatchUnit.NONE,
            attributes = attributes,
        )

        val keys = dimensionSet(json)
        assertEquals(EmfConstants.MAX_DIMENSIONS_PER_SET, keys.size)
        keys.forEach { key ->
            assertTrue(
                json.contains("\"$key\":\"") && json.indexOf("\"$key\":\"") > json.indexOf("\"_aws\""),
                "dimension key '$key' declared in Dimensions but missing as a target member",
            )
        }
    }
}

/**
 * Extracts the single dimension set (the keys inside `Dimensions: [[...]]`) from an EMF document.
 */
private fun dimensionSet(json: String): List<String> {
    val set = checkNotNull("\"Dimensions\":\\[\\[(.*?)]]".toRegex().find(json)) {
        "no Dimensions array found in: $json"
    }.groupValues[1]

    return if (set.isEmpty()) emptyList() else set.split(",").map { it.trim('"') }
}
