/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException

/**
 * A zero-copy cursor over an in-memory CBOR payload.
 *
 * The CBOR deserializer reads from a fully-buffered payload, so rather than routing every read through
 * `SdkBuffer`/okio (which allocates on `peek()` and copies the payload in on construction) this reads
 * directly from the backing [ByteArray] with an integer offset. Lookahead is allocation-free and
 * multi-byte reads / string / byte-array decodes are plain array slices.
 *
 * All reads are big-endian (CBOR wire order). Reads past the end throw [DeserializationException].
 */
internal class CborReader(private val data: ByteArray) {
    private var pos: Int = 0

    /** For test compatibility: build a reader from any buffered source by draining it once. */
    constructor(buffer: SdkBufferedSource) : this(buffer.readByteArray())

    fun exhausted(): Boolean = pos >= data.size

    private fun ensure(n: Int) {
        if (n < 0 || pos + n > data.size) {
            throw DeserializationException("Unexpected end of CBOR payload: needed $n byte(s) at offset $pos of ${data.size}")
        }
    }

    /** Return the next byte without consuming it. Allocation-free lookahead. */
    fun peekByte(): Byte {
        ensure(1)
        return data[pos]
    }

    fun readByte(): Byte {
        ensure(1)
        return data[pos++]
    }

    fun readShort(): Short {
        ensure(2)
        val v = ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)
        pos += 2
        return v.toShort()
    }

    fun readInt(): Int {
        ensure(4)
        var v = 0
        for (i in 0 until 4) v = (v shl 8) or (data[pos + i].toInt() and 0xff)
        pos += 4
        return v
    }

    fun readLong(): Long {
        ensure(8)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (data[pos + i].toLong() and 0xff)
        pos += 8
        return v
    }

    fun readByteArray(byteCount: Int): ByteArray {
        ensure(byteCount)
        val out = data.copyOfRange(pos, pos + byteCount)
        pos += byteCount
        return out
    }

    fun readUtf8(byteCount: Int): String {
        ensure(byteCount)
        val s = data.decodeToString(pos, pos + byteCount)
        pos += byteCount
        return s
    }
}
