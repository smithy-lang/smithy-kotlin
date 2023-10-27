/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util.text.encoding

import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {
    @Test
    fun testUnicodeDecoding() {
        val encoded = "f%F0%9F%98%81o"
        val decoded = Encoding.Query.decode(encoded)
        assertEquals("f😁o", decoded)
    }

    @Test
    fun testUnicodeEncoding() {
        val decoded = "f😁o"
        val encoded = Encoding.Query.encode(decoded)
        assertEquals("f%F0%9F%98%81o", encoded)
    }
}
