/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import software.aws.clientrt.http.QueryParameters
import kotlin.test.Test
import kotlin.test.assertTrue

class TextTest {

    @Test
    fun splitQueryStringIntoParts() {
        val query = "foo=baz&bar=quux&foo=qux&a="
        val actual = query.splitAsQueryParameters()
        val expected = QueryParameters {
            append("foo", "baz")
            append("foo", "qux")
            append("bar", "quux")
            append("a", "")
        }

        expected.entries().forEach { entry ->
            entry.value.forEach { value ->
                assertTrue(actual.contains(entry.key, value), "parsed query does not contain ${entry.key}:$value")
            }
        }

        val queryNoEquals = "abc=cde&noequalssign"
        val actualNoEquals = queryNoEquals.splitAsQueryParameters()
        val expectedNoEquals = QueryParameters {
            append("abc", "cde")
            append("noequalssign", "")
        }
        expectedNoEquals.entries().forEach { entry ->
            entry.value.forEach { value ->
                assertTrue(actualNoEquals.contains(entry.key, value), "parsed query does not contain ${entry.key}:$value")
            }
        }
    }
}
