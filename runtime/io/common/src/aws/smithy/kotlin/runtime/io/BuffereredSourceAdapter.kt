/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toOkio
import aws.smithy.kotlin.runtime.io.internal.toSdk

internal expect class BufferedSourceAdapter(source: okio.BufferedSource) : SdkBufferedSource

// base class that fills in most of the common implementation, platforms just need to implement the platform specific
// part of the interface
internal abstract class AbstractBufferedSourceAdapter(
    protected val delegate: okio.BufferedSource,
) : SdkBufferedSource {
    override val buffer: SdkBuffer
        get() = delegate.buffer.toSdk()

    override fun skip(byteCount: Long): Unit = delegate.skip(byteCount)

    override fun readByte(): Byte = delegate.readByte()

    override fun readShort(): Short = delegate.readShort()

    override fun readShortLe(): Short = delegate.readShortLe()

    override fun readLong(): Long = delegate.readLong()

    override fun readLongLe(): Long = delegate.readLongLe()

    override fun readInt(): Int = delegate.readInt()

    override fun readIntLe(): Int = delegate.readIntLe()

    override fun readAll(sink: SdkSink): Long =
        delegate.readAll(sink.toOkio())

    override fun read(sink: ByteArray, offset: Int, limit: Int): Int =
        delegate.read(sink, offset, limit)

    override fun read(sink: SdkBuffer, limit: Long): Long =
        delegate.read(sink.toOkio(), limit)

    override fun readByteArray(): ByteArray = delegate.readByteArray()

    override fun readByteArray(byteCount: Long): ByteArray = delegate.readByteArray(byteCount)

    override fun readUtf8(): String = delegate.readUtf8()

    override fun readUtf8(byteCount: Long): String = delegate.readUtf8(byteCount)

    override fun peek(): SdkBufferedSource =
        delegate.peek().toSdk().buffer()

    override fun request(byteCount: Long): Boolean = delegate.request(byteCount)

    override fun require(byteCount: Long): Unit = delegate.require(byteCount)

    override fun close() = delegate.close()
}
