/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.smithy.Document
import aws.smithy.kotlin.runtime.smithy.buildDocument
import kotlin.test.*

class DocumentBuilderTest {
    @Test
    fun itBuildsAnObject() {
        val doc = buildDocument {
            "foo" to 1
            "baz" to buildList {
                add(202L)
                add(12)
                add(true)
                add("blah")
                add(null)
                addAll(listOf(9, 10, 12))
            }
            "qux" to null
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah",null,9,10,12],"qux":null}"""

        assertEquals(expected, "$doc")
        assertEquals(1, doc.asMap()["foo"]?.asInt())
    }

    @Test
    fun itRejectsDoubleInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsDoubleNegativeInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be -Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Double.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsDoubleNaN() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be NaN, as its value cannot be preserved across serde"
        ) {
            Document(Double.NaN)
        }
    }

    @Test
    fun itRejectsFloatInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsFloatNegativeInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be -Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Float.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsFloatNaN() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be NaN, as its value cannot be preserved across serde"
        ) {
            Document(Float.NaN)
        }
    }
}
