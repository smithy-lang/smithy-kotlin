/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.smithy.Document
import kotlin.test.*

class DocumentBuilderTest {
    @Test
    fun buildsAnObject() {
        val doc = Document {
            "foo" to 1
            "baz" to Document.listOf(
                202L,
                12,
                true,
                "blah"
            )
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah"]}"""

        assertEquals(expected, "$doc")
    }
}
