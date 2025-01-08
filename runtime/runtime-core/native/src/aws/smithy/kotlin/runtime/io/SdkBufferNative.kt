/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.*

public actual class SdkBuffer :
    SdkBufferedSource,
    SdkBufferedSink {
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

    actual override fun write(source: SdkBuffer, byteCount: Long): Unit = commonWrite(source, byteCount)

    actual override fun write(source: ByteArray, offset: Int, limit: Int): Unit = commonWrite(source, offset, limit)

    actual override fun write(source: SdkSource, byteCount: Long): Unit = commonWrite(source, byteCount)

    actual override fun writeAll(source: SdkSource): Long = commonWriteAll(source)

    actual override fun writeUtf8(string: String, start: Int, endExclusive: Int): Unit = commonWriteUtf8(string, start, endExclusive)

    actual override fun writeByte(x: Byte): Unit = commonWriteByte(x)

    actual override fun writeShort(x: Short): Unit = commonWriteShort(x)

    actual override fun writeShortLe(x: Short): Unit = commonWriteShortLe(x)

    actual override fun writeInt(x: Int): Unit = commonWriteInt(x)

    actual override fun writeIntLe(x: Int): Unit = commonWriteIntLe(x)

    actual override fun writeLong(x: Long): Unit = commonWriteLong(x)

    actual override fun writeLongLe(x: Long): Unit = commonWriteLongLe(x)

    actual override fun flush(): Unit = commonFlush()

    actual override fun emit() {
        inner.emit()
    }

    actual override fun skip(byteCount: Long): Unit = commonSkip(byteCount)

    actual override fun readByte(): Byte = commonReadByte()

    actual override fun readShort(): Short = commonReadShort()

    actual override fun readShortLe(): Short = commonReadShortLe()

    actual override fun readLong(): Long = commonReadLong()

    actual override fun readLongLe(): Long = commonReadLongLe()

    actual override fun readInt(): Int = commonReadInt()

    actual override fun readIntLe(): Int = commonReadIntLe()

    actual override fun readAll(sink: SdkSink): Long = commonReadAll(sink)

    actual override fun read(sink: ByteArray, offset: Int, limit: Int): Int = commonRead(sink, offset, limit)

    actual override fun readByteArray(): ByteArray = commonReadByteArray()

    actual override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

    actual override fun readUtf8(): String = commonReadUtf8()

    actual override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

    actual override fun peek(): SdkBufferedSource = commonPeek()

    actual override fun exhausted(): Boolean = commonExhausted()

    actual override fun request(byteCount: Long): Boolean = commonRequest(byteCount)

    actual override fun require(byteCount: Long): Unit = commonRequire(byteCount)

    actual override fun read(sink: SdkBuffer, limit: Long): Long = commonRead(sink, limit)

    actual override fun close(): Unit = commonClose()
}
