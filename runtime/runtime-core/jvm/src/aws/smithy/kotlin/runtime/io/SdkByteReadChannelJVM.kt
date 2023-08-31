/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.*

/**
 * Create a blocking [InputStream] that blocks everytime the channel suspends at [SdkByteReadChannel.read]
 */
public fun SdkByteReadChannel.toInputStream(): InputStream = InputAdapter(this)

private const val DEFAULT_READ_BYTES = 8192L
private class InputAdapter(private val ch: SdkByteReadChannel) : InputStream() {

    private val buffer = SdkBuffer()

    override fun read(): Int {
        if (ch.isClosedForRead && buffer.size == 0L) return -1

        if (buffer.size == 0L) {
            val rc = readBlocking()
            if (rc == -1L) return -1
        }

        return buffer.readByte().toInt() and 0xff
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (ch.isClosedForRead && buffer.size == 0L) return -1
        if (buffer.size == 0L) {
            val rc = readBlocking()
            if (rc == -1L) return -1
        }

        return buffer.read(b, off, len)
    }

    private fun readBlocking(): Long =
        runBlocking {
            ch.read(buffer, DEFAULT_READ_BYTES)
        }

    override fun available(): Int = ch.availableForRead

    override fun close() {
        super.close()
        ch.cancel(null)
    }
}
