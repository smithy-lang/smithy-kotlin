/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.bits.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllocatorTest {
    @Test
    fun testRealloc() {
        val contents = byteArrayOf(5, 6, 7)
        val m1 = DefaultAllocator.alloc(128)
        m1.storeByteArray(2, contents)
        val m2 = DefaultAllocator.realloc(m1, 512)
        val buf = ByteArray(3)
        m2.loadByteArray(2, buf)
        assertTrue { buf.contentEquals(contents) }
        DefaultAllocator.free(m2)
    }

    @Test
    fun testCeilPower2() {
        val tests = listOf(
            0 to 0,
            1 to 1,
            2 to 2,
            3 to 4,
            4 to 4,
            5 to 8,
            17 to 32
        )
        tests.forEach { (input, expected) -> assertEquals(expected, ceilp2(input)) }
    }
}
