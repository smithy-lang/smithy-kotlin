/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.smithy.buildDocument
import kotlin.test.*

class DocumentBuilderTest {
    @Test
    fun buildsAnObject() {
        val doc = buildDocument {
            "foo" to 1
            "baz" to buildList {
                add(202L)
                add(12)
                add(true)
                add("blah")
                add(null)
            }
            "qux" to null
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah",null],"qux":null}"""

        assertEquals(expected, "$doc")
        assertEquals(1, doc.asMap()["foo"]?.asInt())
    }
}
