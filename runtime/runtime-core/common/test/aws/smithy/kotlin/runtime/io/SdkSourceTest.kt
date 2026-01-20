/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

class SdkSourceTest {
    @Test
    fun readRemaining() {
        val data = "Hello world"
        val dataLength = data.length.toLong()
        val readCycles = 100
        val totalDataLength = dataLength * readCycles

        // Manual and readRemaining
        val source = createTestSource(data, dataLength, readCycles)
        val buffer = SdkBuffer()
        val manualReads = 10
        repeat(manualReads) {
            source.read(buffer, dataLength)
        }
        var readByReadRemaining = source.readRemaining(buffer)
        assertEquals(readByReadRemaining, totalDataLength - manualReads * dataLength)
        assertEquals(buffer.size, totalDataLength)

        // Only readRemaining
        buffer.skip(totalDataLength)
        readByReadRemaining = createTestSource(data, dataLength, readCycles).readRemaining(buffer)
        assertEquals(readByReadRemaining, totalDataLength)
        assertEquals(buffer.size, totalDataLength)
    }
}

private fun createTestSource(
    data: String,
    dataLength: Long,
    readCycles: Int,
) = object : SdkSource {
    var remainingReadCycles = readCycles

    override fun read(sink: SdkBuffer, limit: Long): Long {
        if (remainingReadCycles == 0) {
            return -1L
        }

        sink.writeUtf8(data)
        remainingReadCycles--
        return dataLength
    }

    override fun close() {}
}
