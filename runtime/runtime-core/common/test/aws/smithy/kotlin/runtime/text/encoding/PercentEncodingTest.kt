/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {
    @Test
    fun testUnicodeDecoding() {
        val encoded = "f%F0%9F%98%81o"
        val decoded = PercentEncoding.Query.decode(encoded)
        assertEquals("f😁o", decoded)
    }

    @Test
    fun testUnicodeEncoding() {
        val decoded = "f😁o"
        val encoded = PercentEncoding.Query.encode(decoded)
        assertEquals("f%F0%9F%98%81o", encoded)
    }

    @Test
    fun testPathDecoding() {
        val encoded = "path%2Fsuffix"
        val decoded = PercentEncoding.Path.decode(encoded)
        assertEquals("path/suffix", decoded)
    }

    @Test
    fun testPathEncoding() {
        val decoded = "path/suffix"
        val encoded = PercentEncoding.Path.encode(decoded)
        assertEquals("path%2Fsuffix", encoded)
    }
}
