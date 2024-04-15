/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

internal actual class BufferedSinkAdapter actual constructor(sink: okio.BufferedSink) : SdkBufferedSink {
    actual override val buffer: SdkBuffer
        get() = TODO("Not yet implemented")

    actual override fun write(source: ByteArray, offset: Int, limit: Int) {
        TODO("Not yet implemented")
    }

    actual override fun writeAll(source: SdkSource): Long {
        TODO("Not yet implemented")
    }

    actual override fun write(source: SdkSource, byteCount: Long) {
        TODO("Not yet implemented")
    }

    actual override fun writeUtf8(string: String, start: Int, endExclusive: Int) {
        TODO("Not yet implemented")
    }

    actual override fun writeByte(x: Byte) {
        TODO("Not yet implemented")
    }

    actual override fun writeShort(x: Short) {
        TODO("Not yet implemented")
    }

    actual override fun writeShortLe(x: Short) {
        TODO("Not yet implemented")
    }

    actual override fun writeInt(x: Int) {
        TODO("Not yet implemented")
    }

    actual override fun writeIntLe(x: Int) {
        TODO("Not yet implemented")
    }

    actual override fun writeLong(x: Long) {
        TODO("Not yet implemented")
    }

    actual override fun writeLongLe(x: Long) {
        TODO("Not yet implemented")
    }

    actual override fun flush() {
        TODO("Not yet implemented")
    }

    actual override fun emit() {
        TODO("Not yet implemented")
    }

    actual override fun write(source: SdkBuffer, byteCount: Long) {
        TODO("Not yet implemented")
    }

    actual override fun close() {
        TODO("Not yet implemented")
    }
}
