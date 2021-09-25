/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import java.nio.ByteBuffer
import kotlin.test.assertEquals

class SdkBufferJVMTest {
    @Test
    fun testReadFully() {
        val src = SdkBuffer.of(byteArrayOf(1, 2, 3, 4, 5))
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
        val src = SdkBuffer.of(byteArrayOf(1, 2, 3, 4, 5)).apply { commitWritten(5) }
        val dest = ByteBuffer.allocate(16)
        src.readAvailable(dest)

        assertEquals(5, dest.position())
        assertEquals(11, dest.remaining())
        for (i in 1..5) {
            assertEquals(i.toByte(), dest.get(i - 1))
        }
    }
}
