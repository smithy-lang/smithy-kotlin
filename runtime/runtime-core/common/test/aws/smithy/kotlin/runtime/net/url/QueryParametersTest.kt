/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.collections.MutableMultiMap
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryParametersTest {
    @Test
    fun testParse() {
        val actual = QueryParameters.parseEncoded("?")
        val expected = QueryParameters { forceQuerySeparator = true }
        assertEquals(expected, actual)
    }

    @Test
    fun testDecodedParametersAlternateEncoding() {
        val paramString = QueryParameters {
            decodedParameters {
                add("a", "green:apple")
                add("b", "yellow:banana")
            }

            decodedParameters(PercentEncoding.FormUrl) {
                add("a", "red:apple")
            }

            val allValues = entryValues.toSet()
            listOf(
                "a" to "green:apple",
                "b" to "yellow:banana",
                "a" to "red%3Aapple",
            ).forEach { (key, value) ->
                assertTrue(allValues.any { it.key.encoded == key && it.value.encoded == value }, dumpMultiMap())
            }
        }.toString()

        assertEquals("?a=green:apple&a=red%3Aapple&b=yellow:banana", paramString)
    }
}

private fun MutableMultiMap<*, *>.dumpMultiMap() = entries.joinToString("", "{\n", "}\n") { (key, values) ->
    val valuesString = values.joinToString("") { "    $it\n" }
    "  $key: [\n$valuesString  ],\n"
}
