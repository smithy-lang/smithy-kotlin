/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.text

import kotlin.test.*

class Utf8Test {

    @Test
    fun testUtf8ByteCount() {
        assertEquals(1, byteCountUtf8("$".encodeToByteArray()[0]))
        assertEquals(2, byteCountUtf8("¢".encodeToByteArray()[0]))
        assertEquals(3, byteCountUtf8("€".encodeToByteArray()[0]))
        assertEquals(4, byteCountUtf8("\uD834\uDD22".encodeToByteArray()[0]))
    }

    @Test
    fun testIsSupplementaryCodePoint() {
        assertFalse(Char.isSupplementaryCodePoint(-1))
        for (c in 0..0xFFFF) {
            assertFalse(Char.isSupplementaryCodePoint(c.toInt()))
        }
        for (c in 0xFFFF + 1..0x10FFFF) {
            assertTrue(Char.isSupplementaryCodePoint(c))
        }
        assertFalse(Char.isSupplementaryCodePoint(0x10FFFF + 1))
    }

    @Test
    fun testCodePointToChars() {
        assertContentEquals(charArrayOf('\uD800', '\uDC00'), Char.codePointToChars(0x010000))
        assertContentEquals(charArrayOf('\uD800', '\uDC01'), Char.codePointToChars(0x010001))
        assertContentEquals(charArrayOf('\uD801', '\uDC01'), Char.codePointToChars(0x010401))
        assertContentEquals(charArrayOf('\uDBFF', '\uDFFF'), Char.codePointToChars(0x10FFFF))

        assertContentEquals(charArrayOf('A'), Char.codePointToChars(65))

        assertFailsWith<IllegalArgumentException>() {
            Char.codePointToChars(Int.MAX_VALUE)
        }
    }
}
