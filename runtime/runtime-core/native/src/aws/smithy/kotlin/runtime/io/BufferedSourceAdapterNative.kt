/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

internal actual class BufferedSourceAdapter actual constructor(source: okio.BufferedSource) : SdkBufferedSource {
    override val buffer: SdkBuffer
        get() = TODO("Not yet implemented")

    override fun skip(byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun readByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun readShort(): Short {
        TODO("Not yet implemented")
    }

    override fun readShortLe(): Short {
        TODO("Not yet implemented")
    }

    override fun readLong(): Long {
        TODO("Not yet implemented")
    }

    override fun readLongLe(): Long {
        TODO("Not yet implemented")
    }

    override fun readInt(): Int {
        TODO("Not yet implemented")
    }

    override fun readIntLe(): Int {
        TODO("Not yet implemented")
    }

    override fun readAll(sink: SdkSink): Long {
        TODO("Not yet implemented")
    }

    override fun read(sink: ByteArray, offset: Int, limit: Int): Int {
        TODO("Not yet implemented")
    }

    override fun readByteArray(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readByteArray(byteCount: Long): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readUtf8(): String {
        TODO("Not yet implemented")
    }

    override fun readUtf8(byteCount: Long): String {
        TODO("Not yet implemented")
    }

    override fun peek(): SdkBufferedSource {
        TODO("Not yet implemented")
    }

    override fun exhausted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun request(byteCount: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun require(byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun read(sink: SdkBuffer, limit: Long): Long {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
