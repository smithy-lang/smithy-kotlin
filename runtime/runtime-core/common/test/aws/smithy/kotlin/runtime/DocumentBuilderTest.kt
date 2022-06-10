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
                add(Document.Null)
                addAll(listOf(9, 10, 12))
            }
            "qux" to Document.Null
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah",null,9,10,12],"qux":null}"""

        assertEquals(expected, "$doc")
        assertEquals(1, doc.asMap()["foo"]?.asInt())
    }

    @Test
    fun itRejectsInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsNegativeInfinity() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be -Infinity, as its value cannot be preserved across serde"
        ) {
            Document(Double.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun itRejectsNaN() {
        assertFailsWith<IllegalArgumentException>(
            "a document number cannot be NaN, as its value cannot be preserved across serde"
        ) {
            Document(Double.NaN)
        }
    }
}
