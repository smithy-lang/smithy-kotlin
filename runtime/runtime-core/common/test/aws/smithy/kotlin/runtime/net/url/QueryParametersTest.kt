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
                add("a", "one green:apple")
                add("b", "two yellow:bananas")
            }

            decodedParameters(PercentEncoding.FormUrl) {
                add("a", "three red:apples")
            }

            val allValues = entryValues.toSet()
            listOf(
                "a" to "one%20green:apple",
                "b" to "two%20yellow:bananas",
                "a" to "three%20red%3Aapples",
            ).forEach { (key, value) ->
                assertTrue(allValues.any { it.key.encoded == key && it.value.encoded == value }, dumpMultiMap())
            }
        }.toString()

        assertEquals("?a=one%20green:apple&a=three%20red%3Aapples&b=two%20yellow:bananas", paramString)
    }
}

private fun MutableMultiMap<*, *>.dumpMultiMap() = entries.joinToString("", "{\n", "}\n") { (key, values) ->
    val valuesString = values.joinToString("") { "    $it\n" }
    "  $key: [\n$valuesString  ],\n"
}
