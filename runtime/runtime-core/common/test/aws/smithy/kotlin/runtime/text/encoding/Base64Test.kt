/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text.encoding

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class Base64Test {
    @Test
    fun itRoundTrips() {
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
            "this is a 32 byte long string!!!" to "dGhpcyBpcyBhIDMyIGJ5dGUgbG9uZyBzdHJpbmchISE=",
        )

        for (test in tests) {
            assertEquals(test.second, test.first.encodeBase64())
            assertEquals(test.first, test.second.decodeBase64())
        }
    }

    @Test
    fun emptyStringTest() {
        assertEquals("", "".encodeBase64())
        assertEquals("", "".decodeBase64())
    }

    @Test
    fun emptyByteArrayTest() {
        val buf = ByteArray(0)
        assertEquals(0, buf.encodeBase64().size)
        assertEquals(0, buf.decodeBase64().size)
    }

    @Test
    fun itHandlesPadding() {
        val cases = mapOf(
            "This" to "VGhpcw==",
            "Thi" to "VGhp",
            "Th" to "VGg=",
            "T" to "VA==",
            "" to "",
        )

        cases.forEach { (text, encodedText) ->
            assertEquals(encodedText, text.encodeBase64())
            assertEquals(text, encodedText.decodeBase64())
        }
    }

    @Test
    fun zeroes() {
        val input = ByteArray(6) { 0 }
        val actual = input.encodeBase64String()
        assertEquals("AAAAAAAA", actual)
    }

    @Test
    fun decodeInvalidBase64String() {
        val ex = assertFails {
            // - is not in the base64 alphabet
            "Zm9v-y==".decodeBase64()
        }
        ex.message!!.shouldContain("decode base64: invalid input byte: 45")
    }

    @Test
    fun decodeInvalidPadding() {
        val ex = assertFails {
            "Zm9vY===".decodeBase64()
        }

        ex.message!!.shouldContain("decode base64: invalid padding")
    }

    @Test
    fun encodeLongerText() {
        val decoded = "Alas, eleventy-one years is far too short a time to live among such excellent and admirable hobbits. I don't know half of you half as well as I should like, and I like less than half of you half as well as you deserve."
        val encoded = "QWxhcywgZWxldmVudHktb25lIHllYXJzIGlzIGZhciB0b28gc2hvcnQgYSB0aW1lIHRvIGxpdmUgYW1vbmcgc3VjaCBleGNlbGxlbnQgYW5kIGFkbWlyYWJsZSBob2JiaXRzLiBJIGRvbid0IGtub3cgaGFsZiBvZiB5b3UgaGFsZiBhcyB3ZWxsIGFzIEkgc2hvdWxkIGxpa2UsIGFuZCBJIGxpa2UgbGVzcyB0aGFuIGhhbGYgb2YgeW91IGhhbGYgYXMgd2VsbCBhcyB5b3UgZGVzZXJ2ZS4="
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }

    @Test
    fun itHandlesUtf8() {
        val decoded = "ユニコードとはか？"
        val encoded = "44Om44OL44Kz44O844OJ44Go44Gv44GL77yf"
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }

    @Test
    fun itHandlesControlChars() {
        val decoded = "hello\tworld\n"
        val encoded = "aGVsbG8Jd29ybGQK"
        assertEquals(encoded, decoded.encodeBase64())
        assertEquals(decoded, encoded.decodeBase64())
    }

    @Test
    fun testUnpaddedInputs() {
        // from https://github.com/smithy-lang/smithy/pull/2502
        val input = "v2hkZWZhdWx0c79tZGVmYXVsdFN0cmluZ2JoaW5kZWZhdWx0Qm9vbGVhbvVrZGVmYXVsdExpc3Sf/3BkZWZhdWx0VGltZXN0YW1wwQBrZGVmYXVsdEJsb2JDYWJja2RlZmF1bHRCeXRlAWxkZWZhdWx0U2hvcnQBbmRlZmF1bHRJbnRlZ2VyCmtkZWZhdWx0TG9uZxhkbGRlZmF1bHRGbG9hdPo/gAAAbWRlZmF1bHREb3VibGX6P4AAAGpkZWZhdWx0TWFwv/9rZGVmYXVsdEVudW1jRk9PbmRlZmF1bHRJbnRFbnVtAWtlbXB0eVN0cmluZ2BsZmFsc2VCb29sZWFu9GllbXB0eUJsb2JAaHplcm9CeXRlAGl6ZXJvU2hvcnQAa3plcm9JbnRlZ2VyAGh6ZXJvTG9uZwBpemVyb0Zsb2F0+gAAAABqemVyb0RvdWJsZfoAAAAA//8"
        input.decodeBase64()

        val inputs = mapOf<String, String>(
            "YQ" to "a",
            "Yg" to "b",
            "YWI" to "ab",
            "YWJj" to "abc",
            "SGVsbG8gd29ybGQ" to "Hello world",
        )

        inputs.forEach { (encoded, expected) ->
            val actual = encoded.decodeBase64()
            assertEquals(expected, actual)
        }
    }
}
