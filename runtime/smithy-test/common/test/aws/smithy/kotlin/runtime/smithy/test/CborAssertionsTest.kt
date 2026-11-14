/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import kotlin.test.Test
import kotlin.test.assertFails

class CborAssertionsTest {
    private fun assertEqual(expectedHex: String, actualHex: String) = assertCborEqual(expectedHex.decodeHexBytes(), actualHex.decodeHexBytes(), "")

    private fun assertNotEqual(expectedHex: String, actualHex: String) {
        assertFails { assertEqual(expectedHex, actualHex) }
    }

    @Test
    fun testMapKeyOrderIgnored() {
        // {"a": 1, "b": 2} vs {"b": 2, "a": 1}
        assertEqual("a2616101616202", "a2616202616101")
    }

    @Test
    fun testIndefiniteLengthsMatchDefinite() {
        // {"a": [1, 2]} definite vs indefinite map and array
        assertEqual("a16161820102", "bf61619f0102ffff")
        // "ab" definite vs indefinite text string in two chunks
        assertEqual("626162", "7f61616162ff")
    }

    @Test
    fun testFloatWidthsWithSameValueMatch() {
        // 1.5 as half, single, and double precision
        assertEqual("f93e00", "fa3fc00000")
        assertEqual("f93e00", "fb3ff8000000000000")
    }

    @Test
    fun testIntegers() {
        assertEqual("1903e8", "1a000003e8") // 1000 encoded with different argument widths
        assertNotEqual("20", "00") // -1 vs 0
        // smallest negative 64-bit argument: -2^64
        assertEqual("3bffffffffffffffff", "3bffffffffffffffff")
    }

    @Test
    fun testTypeDifferencesFail() {
        assertNotEqual("01", "f93c00") // integer 1 vs float 1.0
        assertNotEqual("c11a5f5e1000", "1a5f5e1000") // tag 1 (epoch timestamp) vs bare integer
        assertNotEqual("4161", "6161") // byte string vs text string
    }

    @Test
    fun testDifferentValuesFail() {
        assertNotEqual("a1616101", "a1616102")
    }

    @Test
    fun testTrailingBytesFail() {
        assertNotEqual("01", "0101")
    }
}
