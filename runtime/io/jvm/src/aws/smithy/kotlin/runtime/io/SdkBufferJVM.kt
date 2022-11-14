/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

public actual class SdkBuffer : SdkBufferedSource, SdkBufferedSink {
    public actual constructor() : this(okio.Buffer())

    internal actual val inner: okio.Buffer

    internal actual constructor(buffer: okio.Buffer) {
        this.inner = buffer
    }

    public actual val size: Long
        get() = inner.size

    actual override val buffer: SdkBuffer
        get() = this

    override fun toString(): String = inner.toString()

    override fun hashCode(): Int = inner.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SdkBuffer) return false
        return inner == other.inner
    }

    override fun skip(byteCount: Long): Unit = commonSkip(byteCount)

    override fun readByte(): Byte = commonReadByte()

    override fun readShort(): Short = commonReadShort()

    override fun readShortLe(): Short = commonReadShortLe()

    override fun readLong(): Long = commonReadLong()

    override fun readLongLe(): Long = commonReadLongLe()

    override fun readInt(): Int = commonReadInt()

    override fun readIntLe(): Int = commonReadIntLe()

    override fun readAll(sink: SdkSink): Long = commonReadAll(sink)

    override fun read(sink: ByteArray, offset: Int, limit: Int): Int =
        commonRead(sink, offset, limit)

    override fun read(sink: SdkBuffer, limit: Long): Long =
        commonRead(sink, limit)

    override fun read(dst: ByteBuffer): Int = inner.read(dst)

    override fun readByteArray(): ByteArray = commonReadByteArray()

    override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

    override fun readUtf8(): String = commonReadUtf8()

    override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

    override fun peek(): SdkBufferedSource = commonPeek()

    override fun request(byteCount: Long): Boolean = commonRequest(byteCount)

    override fun require(byteCount: Long): Unit = commonRequire(byteCount)

    override fun write(source: ByteArray, offset: Int, limit: Int): Unit =
        commonWrite(source, offset, limit)

    override fun write(source: SdkSource, byteCount: Long): Unit =
        commonWrite(source, byteCount)

    override fun write(source: SdkBuffer, byteCount: Long): Unit =
        commonWrite(source, byteCount)

    override fun write(src: ByteBuffer): Int = inner.write(src)

    override fun writeAll(source: SdkSource): Long = commonWriteAll(source)

    override fun writeUtf8(string: String, start: Int, endExclusive: Int): Unit =
        commonWriteUtf8(string, start, endExclusive)

    override fun writeByte(x: Byte): Unit = commonWriteByte(x)

    override fun writeShort(x: Short): Unit = commonWriteShort(x)

    override fun writeShortLe(x: Short): Unit = commonWriteShortLe(x)

    override fun writeInt(x: Int): Unit = commonWriteInt(x)

    override fun writeIntLe(x: Int): Unit = commonWriteIntLe(x)

    override fun writeLong(x: Long): Unit = commonWriteLong(x)

    override fun writeLongLe(x: Long): Unit = commonWriteLongLe(x)

    override fun flush(): Unit = commonFlush()

    override fun emit() {
        inner.emit()
    }
    override fun close(): Unit = commonClose()
    override fun isOpen(): Boolean = inner.isOpen

    override fun inputStream(): InputStream = inner.inputStream()
    override fun outputStream(): OutputStream = inner.outputStream()
}
