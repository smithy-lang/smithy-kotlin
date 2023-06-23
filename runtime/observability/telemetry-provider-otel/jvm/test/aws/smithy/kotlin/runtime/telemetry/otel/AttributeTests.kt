/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.attributesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import io.opentelemetry.api.common.AttributeKey as OtelAttributeKey

class AttributeTests {
    @Test
    fun testToOtelAttributeKey() {
        val strAttr = AttributeKey<String>("key")
        val otelStrKey = strAttr.otelAttrKeyOrNull("foo")
        assertNotNull(otelStrKey)
        assertIs<OtelAttributeKey<String>>(otelStrKey)

        val longAttr = AttributeKey<Long>("key")
        val otelLongKey = longAttr.otelAttrKeyOrNull(0L)
        assertNotNull(otelLongKey)
        assertIs<OtelAttributeKey<Long>>(otelLongKey)

        val boolAttr = AttributeKey<Boolean>("key")
        val otelBoolKey = boolAttr.otelAttrKeyOrNull(true)
        assertNotNull(otelBoolKey)
        assertIs<OtelAttributeKey<Boolean>>(otelBoolKey)

        val doubleAttr = AttributeKey<Double>("key")
        val otelDoubleKey = doubleAttr.otelAttrKeyOrNull(0.0)
        assertNotNull(otelDoubleKey)
        assertIs<OtelAttributeKey<Double>>(otelDoubleKey)

        val strArrayAttr = AttributeKey<List<String>>("key")
        val otelStrArrayKey = strArrayAttr.otelAttrKeyOrNull(listOf("foo"))
        assertNotNull(otelStrArrayKey)
        assertIs<OtelAttributeKey<List<String>>>(otelStrArrayKey)

        val longArrayAttr = AttributeKey<List<Long>>("key")
        val otelLongArrayKey = longArrayAttr.otelAttrKeyOrNull(listOf(0L))
        assertNotNull(otelLongArrayKey)
        assertIs<OtelAttributeKey<List<Long>>>(otelLongArrayKey)

        val boolArrayAttr = AttributeKey<List<Boolean>>("key")
        val otelBoolArrayKey = boolArrayAttr.otelAttrKeyOrNull(listOf(true))
        assertNotNull(otelBoolArrayKey)
        assertIs<OtelAttributeKey<List<Boolean>>>(otelBoolArrayKey)

        val doubleArrayAttr = AttributeKey<List<Double>>("key")
        val otelDoubleArrayKey = doubleArrayAttr.otelAttrKeyOrNull(listOf(0.0))
        assertNotNull(otelDoubleArrayKey)
        assertIs<OtelAttributeKey<List<Double>>>(otelDoubleArrayKey)
    }

    @Test
    fun testToOtelAttributes() {
        val attr = attributesOf {
            "str" to "foo"
            "bool" to true
            "long" to 1L
        }

        val actual = attr.toOtelAttributes()
        assertEquals("foo", actual.get(OtelAttributeKey.stringKey("str")))
        assertEquals(true, actual.get(OtelAttributeKey.booleanKey("bool")))
        assertEquals(1L, actual.get(OtelAttributeKey.longKey("long")))
    }
}
