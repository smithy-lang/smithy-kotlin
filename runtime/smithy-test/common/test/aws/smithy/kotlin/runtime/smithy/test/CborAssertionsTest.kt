/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CborAssertionsTest {
    private fun assertEqual(expectedHex: String, actualHex: String) = assertCborEqual(expectedHex.decodeHexBytes(), actualHex.decodeHexBytes(), "")

    // the documents are well formed but differ semantically
    private fun assertNotEqual(expectedHex: String, actualHex: String) {
        assertFailsWith<AssertionError> { assertEqual(expectedHex, actualHex) }
    }

    private fun assertMalformed(hex: String) {
        assertFailsWith<IllegalStateException> { assertEqual(hex, hex) }
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
    fun testNaNsAreEqualAcrossWidths() {
        assertEqual("f97e00", "fa7fc00000")
        assertEqual("f97e00", "fb7ff8000000000000")
    }

    @Test
    fun testSignedZerosDiffer() {
        assertEqual("f98000", "fa80000000")
        assertNotEqual("f98000", "f90000")
    }

    @Test
    fun testInfinitiesAndSubnormals() {
        assertEqual("f97c00", "fa7f800000")
        assertNotEqual("f97c00", "f9fc00")
        // smallest positive half subnormal, 2^-24
        assertEqual("f90001", "fb3e70000000000000")
    }

    @Test
    fun testIntegers() {
        assertEqual("1903e8", "1a000003e8") // 1000 encoded with different argument widths
        assertNotEqual("20", "00") // -1 vs 0
        assertNotEqual("1bffffffffffffffff", "3bffffffffffffffff") // 2^64 - 1 vs -2^64
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
    fun testTrailingBytesAreMalformed() = assertMalformed("0101")

    @Test
    fun testTruncatedInputIsMalformed() {
        assertMalformed("62")
        assertMalformed("1a0000")
    }

    @Test
    fun testOversizedLengthsAreMalformed() {
        // byte string with a length of 2^32 + 3 must not be read as a length of 3
        assertMalformed("5b0000000100000003616263")
        // array claiming Int.MAX_VALUE items with no input
        assertMalformed("9a7fffffff")
        // map claiming 2^62 entries
        assertMalformed("bb4000000000000000")
    }

    @Test
    fun testDuplicateMapKeysAreMalformed() = assertMalformed("a2616101616102")

    @Test
    fun testReservedAdditionalInfoIsMalformed() {
        assertMalformed("fc") // major 7, reserved 28
        assertMalformed("1c") // major 0, reserved 28
        assertMalformed("1f") // major 0, indefinite
        assertMalformed("df") // major 6, indefinite
    }

    @Test
    fun testNonMinimalSimpleValueIsMalformed() = assertMalformed("f814")

    @Test
    fun testMalformedIndefiniteStringsAreMalformed() {
        assertMalformed("7f4161ff") // byte string chunk in a text string
        assertMalformed("7f7f6161ffff") // nested indefinite chunk
        assertMalformed("7f6161") // no break
    }

    @Test
    fun testInvalidUtf8IsMalformed() = assertMalformed("61ff")

    @Test
    fun testBreakInValuePositionIsMalformed() {
        assertMalformed("ff")
        assertMalformed("81ff")
        assertMalformed("c1ff")
    }

    @Test
    fun testExcessiveNestingIsMalformed() {
        // 63 nested arrays around a leaf is the deepest accepted document
        assertEqual("81".repeat(63) + "00", "81".repeat(63) + "00")
        assertMalformed("81".repeat(65) + "00")
    }

    @Test
    fun testNegativeIntegerBounds() {
        // -2^64 is the smallest representable negative integer and must not wrap
        assertEqual("3bffffffffffffffff", "3bffffffffffffffff")
        assertNotEqual("3bffffffffffffffff", "3bfffffffffffffffe")
        assertNotEqual("3bffffffffffffffff", "00")
    }
}
