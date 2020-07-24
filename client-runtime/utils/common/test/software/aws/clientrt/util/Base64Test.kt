/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.util

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class Base64Test {
    @Test
    fun `it round trips`() {
        val tests = listOf(
            "ABC" to "QUJD",
            "Kotlin is awesome" to "S290bGluIGlzIGF3ZXNvbWU=",
            // test cases from aws-crt-common encoding_test.c
            "f" to "Zg==",
            "fo" to "Zm8=",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg==",
            "fooba" to "Zm9vYmE=",
            "foobar" to "Zm9vYmFy",
            "this is a 32 byte long string!!!" to "dGhpcyBpcyBhIDMyIGJ5dGUgbG9uZyBzdHJpbmchISE="
        )

        for (test in tests) {
            assertEquals(test.second, test.first.encodeBase64())
            assertEquals(test.first, test.second.decodeBase64())
        }
    }

    @Test
    fun `empty String test`() {
        assertEquals("", "".encodeBase64())
        assertEquals("", "".decodeBase64())
    }

    @Test
    fun `empty ByteArray test`() {
        val buf = ByteArray(0)
        assertEquals(0, buf.encodeBase64().size)
        assertEquals(0, buf.decodeBase64().size)
    }

    @Test
    fun `it handles padding`() {
        val cases = mapOf(
            "This" to "VGhpcw==",
            "Thi" to "VGhp",
            "Th" to "VGg=",
            "T" to "VA==",
            "" to ""
        )

        cases.forEach { (text, encodedText) ->
            assertEquals(encodedText, text.encodeBase64())
            assertEquals(text, encodedText.decodeBase64())
        }
    }

    @Test
    fun `zeroes`() {
        val input = ByteArray(6) { 0 }
        val actual = input.encodeBase64()
        for (i in actual.indices) {
            // AAAAAA
            assertEquals(65, actual[i])
        }
    }

    @Test
    fun `decode invalid base64 string`() {
        val ex = assertFails {
            // - is not in the base64 alphabet
            "Zm9v-y==".decodeBase64()
        }
        ex.message!!.shouldContain("decode base64: invalid input byte: 45")
    }

    @Test
    fun `decode non multiple of 4`() {
        val ex = assertFails {
            "Zm9vY=".decodeBase64()
        }
        ex.message!!.shouldContain("invalid base64 string of length 6; not a multiple of 4")
    }

    @Test
    fun `decode invalid padding`() {
        val ex = assertFails {
            "Zm9vY===".decodeBase64()
        }

        ex.message!!.shouldContain("decode base64: invalid padding")
    }

    @Test
    fun `encode longer text`() {
        val decoded = "Alas, eleventy-one years is far too short a time to live among such excellent and admirable hobbits. I don't know half of you half as well as I should like, and I like less than half of you half as well as you deserve."
        val encoded = "QWxhcywgZWxldmVudHktb25lIHllYXJzIGlzIGZhciB0b28gc2hvcnQgYSB0aW1lIHRvIGxpdmUgYW1vbmcgc3VjaCBleGNlbGxlbnQgYW5kIGFkbWlyYWJsZSBob2JiaXRzLiBJIGRvbid0IGtub3cgaGFsZiBvZiB5b3UgaGFsZiBhcyB3ZWxsIGFzIEkgc2hvdWxkIGxpa2UsIGFuZCBJIGxpa2UgbGVzcyB0aGFuIGhhbGYgb2YgeW91IGhhbGYgYXMgd2VsbCBhcyB5b3UgZGVzZXJ2ZS4="
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }

    @Test
    fun `it handles utf8`() {
        val decoded = "ユニコードとはか？"
        val encoded = "44Om44OL44Kz44O844OJ44Go44Gv44GL77yf"
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }

    @Test
    fun `it handles control chars`() {
        val decoded = "hello\tworld\n"
        val encoded = "aGVsbG8Jd29ybGQK"
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }
}
