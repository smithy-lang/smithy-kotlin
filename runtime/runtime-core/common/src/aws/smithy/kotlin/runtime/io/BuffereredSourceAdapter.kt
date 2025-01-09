/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toOkio
import aws.smithy.kotlin.runtime.io.internal.toSdk

internal expect class BufferedSourceAdapter(source: okio.BufferedSource) : SdkBufferedSource {
    override val buffer: SdkBuffer
    override fun read(sink: SdkBuffer, limit: Long): Long
    override fun readByte(): Byte
    override fun read(sink: ByteArray, offset: Int, limit: Int): Int
    override fun readByteArray(): ByteArray
    override fun readInt(): Int
    override fun readIntLe(): Int
    override fun readLongLe(): Long
    override fun readLong(): Long
    override fun readShort(): Short
    override fun readShortLe(): Short
    override fun readUtf8(): String
    override fun readUtf8(byteCount: Long): String
    override fun readByteArray(byteCount: Long): ByteArray
    override fun request(byteCount: Long): Boolean
    override fun exhausted(): Boolean
    override fun readAll(sink: SdkSink): Long
    override fun require(byteCount: Long)
    override fun skip(byteCount: Long)
    override fun peek(): SdkBufferedSource
    override fun close()
}

/**
 * Used to wrap calls to Okio, catching Okio exceptions (e.g. okio.EOFException) and throwing our own (e.g. aws.smithy.kotlin.runtime.io.EOFException).
 */
internal inline fun <T> SdkBufferedSource.wrapOkio(block: SdkBufferedSource.() -> T): T = try {
    block()
} catch (e: okio.EOFException) {
    throw EOFException("Okio operation failed", e)
} catch (e: okio.IOException) {
    throw IOException("Okio operation failed", e)
}

// base class that fills in most of the common implementation, platforms just need to implement the platform specific
// part of the interface
internal abstract class AbstractBufferedSourceAdapter(
    protected val delegate: okio.BufferedSource,
) : SdkBufferedSource {
    override val buffer: SdkBuffer
        get() = delegate.buffer.toSdk()

    override fun skip(byteCount: Long): Unit = wrapOkio { delegate.skip(byteCount) }

    override fun readByte(): Byte = wrapOkio { delegate.readByte() }

    override fun readShort(): Short = wrapOkio { delegate.readShort() }

    override fun readShortLe(): Short = wrapOkio { delegate.readShortLe() }

    override fun readLong(): Long = wrapOkio { delegate.readLong() }

    override fun readLongLe(): Long = wrapOkio { delegate.readLongLe() }

    override fun readInt(): Int = wrapOkio { delegate.readInt() }

    override fun readIntLe(): Int = wrapOkio { delegate.readIntLe() }

    override fun readAll(sink: SdkSink): Long = wrapOkio {
        delegate.readAll(sink.toOkio())
    }

    override fun read(sink: ByteArray, offset: Int, limit: Int): Int = wrapOkio {
        delegate.read(sink, offset, limit)
    }

    override fun read(sink: SdkBuffer, limit: Long): Long = wrapOkio {
        delegate.read(sink.toOkio(), limit)
    }

    override fun readByteArray(): ByteArray = wrapOkio { delegate.readByteArray() }

    override fun readByteArray(byteCount: Long): ByteArray = wrapOkio {
        delegate.readByteArray(byteCount)
    }

    override fun readUtf8(): String = wrapOkio { delegate.readUtf8() }

    override fun readUtf8(byteCount: Long): String = wrapOkio {
        delegate.readUtf8(byteCount)
    }

    override fun peek(): SdkBufferedSource = wrapOkio {
        delegate.peek().toSdk().buffer()
    }

    override fun exhausted(): Boolean = wrapOkio { delegate.exhausted() }

    override fun request(byteCount: Long): Boolean = wrapOkio {
        delegate.request(byteCount)
    }

    override fun require(byteCount: Long): Unit = wrapOkio { delegate.require(byteCount) }

    override fun close() = wrapOkio { delegate.close() }
}
