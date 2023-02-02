/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HexTest {
    // encoded to byte array
    private val tests = listOf<Pair<String, ByteArray>>(
        "" to ByteArray(0),
        "01" to byteArrayOf(1),
        "01020304" to byteArrayOf(1, 2, 3, 4),
        "000102030405060708090a0b0c0d0e0f" to byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f),
        "4d616469736f6e" to byteArrayOf(77, 97, 100, 105, 115, 111, 110),
        // boundaries and limits
        "ff" to byteArrayOf(-1),
        "0f" to byteArrayOf(15),
        "10" to byteArrayOf(16),
        "f0" to byteArrayOf(-16),
    )

    @Test
    fun testEncode() {
        for (test in tests) {
            assertEquals(test.first, test.second.encodeToHex())
        }
    }

    @Test
    fun testDecode() {
        for (test in tests) {
            assertContentEquals(test.second, test.first.decodeHexBytes())
        }
    }

    @Test
    fun testFooBar() {
        assertEquals("666f6f626172", encodeHex("foobar".encodeToByteArray()))
        assertEquals("foobar", decodeHex("666f6f626172").decodeToString())
    }

    @Test
    fun testMissingLeadingZero() {
        assertContentEquals(byteArrayOf(1, 2, 3, 4), decodeHex("1020304"))
    }
}
