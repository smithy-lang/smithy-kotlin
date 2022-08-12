/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkByteBufferJVMTest {
    @Test
    fun testReadFully() {
        val src = SdkByteBuffer.of(byteArrayOf(1, 2, 3, 4, 5))
        val dest = ByteBuffer.allocate(3)
        src.readFully(dest)

        assertEquals(3, dest.position())
        assertEquals(0, dest.remaining())

        for (i in 1..3) {
            assertEquals(i.toByte(), dest.get(i - 1))
        }
    }

    @Test
    fun testReadAvailable() {
        val src = SdkByteBuffer.of(byteArrayOf(1, 2, 3, 4, 5)).apply { advance(5u) }
        val dest = ByteBuffer.allocate(16)
        src.readAvailable(dest)

        assertEquals(5, dest.position())
        assertEquals(11, dest.remaining())
        for (i in 1..5) {
            assertEquals(i.toByte(), dest.get(i - 1))
        }
    }
}
