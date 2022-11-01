/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("NOTHING_TO_INLINE")

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.io.*

internal inline fun SdkBuffer.commonSkip(byteCount: Long) = inner.skip(byteCount)

internal inline fun SdkBuffer.commonReadByte(): Byte = inner.readByte()

internal inline fun SdkBuffer.commonReadShort(): Short = inner.readShort()

internal inline fun SdkBuffer.commonReadShortLe(): Short = inner.readShortLe()

internal inline fun SdkBuffer.commonReadLong(): Long = inner.readLong()

internal inline fun SdkBuffer.commonReadLongLe(): Long = inner.readLongLe()

internal inline fun SdkBuffer.commonReadInt(): Int = inner.readInt()

internal inline fun SdkBuffer.commonReadIntLe(): Int = inner.readIntLe()

internal inline fun SdkBuffer.commonReadAll(sink: SdkSink): Long =
    inner.readAll(sink.toOkio())

internal inline fun SdkBuffer.commonRead(sink: ByteArray, offset: Int, limit: Int): Int =
    inner.read(sink, offset, limit)

internal inline fun SdkBuffer.commonRead(sink: SdkBuffer, limit: Long): Long =
    inner.read(sink.inner, limit)

internal inline fun SdkBuffer.commonReadByteArray(): ByteArray = inner.readByteArray()

internal inline fun SdkBuffer.commonReadUtf8(): String = inner.readUtf8()

internal inline fun SdkBuffer.commonReadUtf8(byteCount: Long): String = inner.readUtf8(byteCount)

internal inline fun SdkBuffer.commonPeek(): SdkBufferedSource = inner.peek().toSdk().buffer()

internal inline fun SdkBuffer.commonWrite(source: ByteArray, offset: Int, limit: Int) {
    inner.write(source, offset, limit)
}

internal inline fun SdkBuffer.commonWrite(source: SdkSource, byteCount: Long) {
    inner.write(source.toOkio(), byteCount)
}
internal inline fun SdkBuffer.commonWrite(source: SdkBuffer, byteCount: Long) {
    inner.write(source.toOkio(), byteCount)
}

internal inline fun SdkBuffer.commonWriteAll(source: SdkSource): Long =
    inner.writeAll(source.toOkio())

internal inline fun SdkBuffer.commonWriteUtf8(string: String, start: Int, endExclusive: Int) {
    inner.writeUtf8(string, start, endExclusive)
}

internal inline fun SdkBuffer.commonWriteByte(x: Byte) { inner.writeByte(x.toInt()) }

internal inline fun SdkBuffer.commonWriteShort(x: Short) { inner.writeShort(x.toInt()) }

internal inline fun SdkBuffer.commonWriteShortLe(x: Short) { inner.writeShortLe(x.toInt()) }

internal inline fun SdkBuffer.commonWriteInt(x: Int) { inner.writeInt(x) }

internal inline fun SdkBuffer.commonWriteIntLe(x: Int) { inner.writeIntLe(x) }

internal inline fun SdkBuffer.commonWriteLong(x: Long) { inner.writeLong(x) }

internal inline fun SdkBuffer.commonWriteLongLe(x: Long) { inner.writeLongLe(x) }

internal inline fun SdkBuffer.commonFlush() { inner.flush() }

internal inline fun SdkBuffer.commonClose() { inner.close() }
