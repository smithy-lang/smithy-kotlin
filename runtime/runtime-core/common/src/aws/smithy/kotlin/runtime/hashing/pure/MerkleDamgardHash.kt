/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.hashing.HashFunction

/**
 * SHA-1 and SHA-256 accept messages shorter than 2^64 bits, i.e. at most 2^61 - 1 bytes. MD5 has no limit (its length
 * field is the bit length mod 2^64), but the same bound keeps `totalBytes * 8` from overflowing.
 */
internal const val MAX_MESSAGE_BYTES: Long = (1L shl 61) - 1

/**
 * Block buffering and length padding shared by MD5, SHA-1, and SHA-256, which all use 64-byte blocks and an 8-byte
 * message-length trailer. They differ only in the compression function and the byte order of the trailer/digest.
 *
 * All whole blocks of one [update] call are handed to the subclass in a single [processBlocks] call, so it can keep
 * its chaining state in locals across blocks. [copy] and [restoreFrom] snapshot the full state for [PureHmac].
 */
internal abstract class MerkleDamgardHash(private val bigEndianLength: Boolean) : HashFunction {
    final override val blockSizeBytes: Int = 64

    private val buffer = ByteArray(64)
    private var bufferLength = 0

    /** Bytes hashed so far. Internal (not private) only so tests can reach the message-length limit. */
    internal var totalBytes = 0L

    /** Compresses [count] consecutive 64-byte blocks starting at [offset] */
    protected abstract fun processBlocks(input: ByteArray, offset: Int, count: Int)
    protected abstract fun resetState()
    protected abstract fun writeDigest(out: ByteArray)

    /** Returns a new instance of the same concrete type, in its initial state */
    protected abstract fun newInstance(): MerkleDamgardHash

    /** Message-schedule scratch space (may be empty), which holds words of the last block compressed */
    internal abstract val scratch: IntArray

    private fun clearScratch() = scratch.fill(0)

    /**
     * Copies the subclass's chaining state from [other]. Implementations must cast [other] to their own type before
     * writing anything, so [restoreFrom] fails cleanly on a mismatch.
     */
    protected abstract fun copyChainingStateFrom(other: MerkleDamgardHash)

    override fun update(input: ByteArray, offset: Int, length: Int) {
        checkRange(input.size, offset, length)
        require(length <= MAX_MESSAGE_BYTES - totalBytes) { "message exceeds $MAX_MESSAGE_BYTES bytes" }
        totalBytes += length
        var off = offset
        var len = length

        if (bufferLength > 0) {
            val n = minOf(64 - bufferLength, len)
            input.copyInto(buffer, bufferLength, off, off + n)
            bufferLength += n
            off += n
            len -= n
            if (bufferLength == 64) {
                processBlocks(buffer, 0, 1)
                bufferLength = 0
            }
        }

        // Compress all whole blocks straight from the input in one call
        val blocks = len ushr 6
        if (blocks > 0) {
            processBlocks(input, off, blocks)
            off += blocks shl 6
            len -= blocks shl 6
        }

        if (len > 0) {
            input.copyInto(buffer, 0, off, off + len)
            bufferLength = len
        }
    }

    override fun digest(): ByteArray {
        val bitLength = totalBytes * 8
        buffer[bufferLength++] = 0x80.toByte()
        if (bufferLength > 56) {
            buffer.fill(0, bufferLength, 64)
            processBlocks(buffer, 0, 1)
            bufferLength = 0
        }
        buffer.fill(0, bufferLength, 56)
        for (i in 0 until 8) {
            val shift = if (bigEndianLength) 56 - 8 * i else 8 * i
            buffer[56 + i] = (bitLength ushr shift).toByte()
        }
        processBlocks(buffer, 0, 1)

        val out = ByteArray(digestSizeBytes)
        writeDigest(out)
        reset()
        return out
    }

    override fun reset() {
        buffer.fill(0) // don't keep message bytes around
        bufferLength = 0
        totalBytes = 0
        clearScratch()
        resetState()
    }

    /**
     * Zeroes the scratch space without touching the hash state. For a hash kept mid-message, such as [PureHmac]'s
     * saved key-pad states, whose scratch would otherwise hold the key-pad words.
     */
    fun wipeScratch(): Unit = clearScratch()

    /** Returns a new instance of the same concrete type, in its initial state, leaving this one untouched */
    fun fresh(): MerkleDamgardHash = newInstance()

    /** Overwrites this hash's entire state with [other]'s, without allocating. [other] must be the same concrete type. */
    fun restoreFrom(other: MerkleDamgardHash) {
        // First, because each subclass casts [other] before writing anything: a type mismatch throws with this hash
        // untouched
        copyChainingStateFrom(other)
        other.buffer.copyInto(buffer, 0, 0, other.bufferLength)
        bufferLength = other.bufferLength
        totalBytes = other.totalBytes
    }

    /** Returns an independent hash in the same state as this one */
    fun copy(): MerkleDamgardHash = newInstance().also { it.restoreFrom(this) }
}

internal fun ByteArray.getIntBe(i: Int): Int = (this[i].toInt() shl 24) or
    ((this[i + 1].toInt() and 0xff) shl 16) or
    ((this[i + 2].toInt() and 0xff) shl 8) or
    (this[i + 3].toInt() and 0xff)

internal fun ByteArray.getIntLe(i: Int): Int = (this[i].toInt() and 0xff) or
    ((this[i + 1].toInt() and 0xff) shl 8) or
    ((this[i + 2].toInt() and 0xff) shl 16) or
    (this[i + 3].toInt() shl 24)

internal fun ByteArray.putIntBe(i: Int, value: Int) {
    this[i] = (value ushr 24).toByte()
    this[i + 1] = (value ushr 16).toByte()
    this[i + 2] = (value ushr 8).toByte()
    this[i + 3] = value.toByte()
}

internal fun ByteArray.putIntLe(i: Int, value: Int) {
    this[i] = value.toByte()
    this[i + 1] = (value ushr 8).toByte()
    this[i + 2] = (value ushr 16).toByte()
    this[i + 3] = (value ushr 24).toByte()
}
