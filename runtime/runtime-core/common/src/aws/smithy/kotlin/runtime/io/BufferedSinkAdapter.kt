/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toOkio
import aws.smithy.kotlin.runtime.io.internal.toSdk

internal expect class BufferedSinkAdapter(sink: okio.BufferedSink) : SdkBufferedSink

// base class that fills in most of the common implementation, platforms just need to implement the platform specific
// part of the interface
internal abstract class AbstractBufferedSinkAdapter(
    protected val delegate: okio.BufferedSink,
) : SdkBufferedSink {
    override fun toString(): String = delegate.toString()

    override val buffer: SdkBuffer
        get() = delegate.buffer.toSdk()

    override fun write(source: ByteArray, offset: Int, limit: Int) {
        delegate.write(source, offset, limit)
    }

    override fun write(source: SdkSource, byteCount: Long) {
        delegate.write(source.toOkio(), byteCount)
    }

    override fun write(source: SdkBuffer, byteCount: Long) {
        delegate.write(source.toOkio(), byteCount)
    }

    override fun writeAll(source: SdkSource): Long =
        delegate.writeAll(source.toOkio())

    override fun writeUtf8(string: String, start: Int, endExclusive: Int) {
        delegate.writeUtf8(string, start, endExclusive)
    }

    override fun writeByte(x: Byte) {
        delegate.writeByte(x.toInt())
    }

    override fun writeShort(x: Short) {
        delegate.writeShort(x.toInt())
    }

    override fun writeShortLe(x: Short) {
        delegate.writeShortLe(x.toInt())
    }

    override fun writeInt(x: Int) {
        delegate.writeInt(x)
    }

    override fun writeIntLe(x: Int) {
        delegate.writeIntLe(x)
    }

    override fun writeLong(x: Long) {
        delegate.writeLong(x)
    }

    override fun writeLongLe(x: Long) {
        delegate.writeLongLe(x)
    }

    override fun flush(): Unit = delegate.flush()

    override fun emit() {
        delegate.emit()
    }

    override fun close() = delegate.close()
}
