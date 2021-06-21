/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.smithy.*
import kotlin.test.*

class DocumentBuilderTest {
    @Test
    fun buildsAnObject() {
        val doc = document {
            "foo" to 1
            "baz" to documentArray {
                +n(202L)
                +n(12)
                +true
                +"blah"
            }
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah"]}"""

        assertEquals(expected, "$doc")
    }
}
