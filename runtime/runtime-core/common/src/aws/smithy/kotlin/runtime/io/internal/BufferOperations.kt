/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("NOTHING_TO_INLINE")

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.io.*

/**
 * Used to wrap calls to Okio, catching Okio exceptions (e.g. okio.EOFException) and throwing our own (e.g. aws.smithy.kotlin.runtime.io.EOFException).
 */
internal inline fun <T> SdkBuffer.wrapOkio(block: SdkBuffer.() -> T): T = try {
    block()
} catch (e: okio.EOFException) {
    throw EOFException("Okio operation failed: ${e.message}", e)
} catch (e: okio.IOException) {
    throw IOException("Okio operation failed: ${e.message}", e)
}

internal inline fun SdkBuffer.commonSkip(byteCount: Long) = wrapOkio { inner.skip(byteCount) }

internal inline fun SdkBuffer.commonReadByte(): Byte = wrapOkio { inner.readByte() }

internal inline fun SdkBuffer.commonReadShort(): Short = wrapOkio { inner.readShort() }

internal inline fun SdkBuffer.commonReadShortLe(): Short = wrapOkio { inner.readShortLe() }

internal inline fun SdkBuffer.commonReadLong(): Long = wrapOkio { inner.readLong() }

internal inline fun SdkBuffer.commonReadLongLe(): Long = wrapOkio { inner.readLongLe() }

internal inline fun SdkBuffer.commonReadInt(): Int = wrapOkio { inner.readInt() }

internal inline fun SdkBuffer.commonReadIntLe(): Int = wrapOkio { inner.readIntLe() }

internal inline fun SdkBuffer.commonReadAll(sink: SdkSink): Long = wrapOkio { inner.readAll(sink.toOkio()) }

internal inline fun SdkBuffer.commonRead(sink: ByteArray, offset: Int, limit: Int): Int =
    wrapOkio { inner.read(sink, offset, limit) }

internal inline fun SdkBuffer.commonRead(sink: SdkBuffer, limit: Long): Long =
    wrapOkio { inner.read(sink.inner, limit) }

internal inline fun SdkBuffer.commonReadByteArray(): ByteArray = wrapOkio { inner.readByteArray() }

internal inline fun SdkBuffer.commonReadByteArray(byteCount: Long): ByteArray = wrapOkio { inner.readByteArray(byteCount) }

internal inline fun SdkBuffer.commonReadUtf8(): String = wrapOkio { inner.readUtf8() }

internal inline fun SdkBuffer.commonReadUtf8(byteCount: Long): String = wrapOkio { inner.readUtf8(byteCount) }

internal inline fun SdkBuffer.commonPeek(): SdkBufferedSource = wrapOkio { inner.peek().toSdk().buffer() }

internal inline fun SdkBuffer.commonExhausted(): Boolean = wrapOkio { inner.exhausted() }

internal inline fun SdkBuffer.commonRequest(byteCount: Long): Boolean = wrapOkio { inner.request(byteCount) }

internal inline fun SdkBuffer.commonRequire(byteCount: Long) = wrapOkio { inner.require(byteCount) }

internal inline fun SdkBuffer.commonWrite(source: ByteArray, offset: Int, limit: Int) {
    wrapOkio { inner.write(source, offset, limit) }
}

internal inline fun SdkBuffer.commonWrite(source: SdkSource, byteCount: Long) {
    wrapOkio { inner.write(source.toOkio(), byteCount) }
}

internal inline fun SdkBuffer.commonWrite(source: SdkBuffer, byteCount: Long) {
    wrapOkio { inner.write(source.toOkio(), byteCount) }
}

internal inline fun SdkBuffer.commonWriteAll(source: SdkSource): Long =
    wrapOkio { inner.writeAll(source.toOkio()) }

internal inline fun SdkBuffer.commonWriteUtf8(string: String, start: Int, endExclusive: Int) {
    wrapOkio { inner.writeUtf8(string, start, endExclusive) }
}

internal inline fun SdkBuffer.commonWriteByte(x: Byte) {
    wrapOkio { inner.writeByte(x.toInt()) }
}

internal inline fun SdkBuffer.commonWriteShort(x: Short) {
    wrapOkio { inner.writeShort(x.toInt()) }
}

internal inline fun SdkBuffer.commonWriteShortLe(x: Short) {
    wrapOkio { inner.writeShortLe(x.toInt()) }
}

internal inline fun SdkBuffer.commonWriteInt(x: Int) {
    wrapOkio { inner.writeInt(x) }
}

internal inline fun SdkBuffer.commonWriteIntLe(x: Int) {
    wrapOkio { inner.writeIntLe(x) }
}

internal inline fun SdkBuffer.commonWriteLong(x: Long) {
    wrapOkio { inner.writeLong(x) }
}

internal inline fun SdkBuffer.commonWriteLongLe(x: Long) {
    wrapOkio { inner.writeLongLe(x) }
}

internal inline fun SdkBuffer.commonFlush() {
    wrapOkio { inner.flush() }
}

internal inline fun SdkBuffer.commonClose() {
    wrapOkio { inner.close() }
}
