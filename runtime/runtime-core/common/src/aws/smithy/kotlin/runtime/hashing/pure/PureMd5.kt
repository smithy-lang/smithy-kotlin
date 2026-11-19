/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

/**
 * Pure-Kotlin MD5 (RFC 1321). Compresses all whole blocks of one update in a single call. The block function is
 * platform-specific ([md5ProcessBlocks]): native and the JVM's JIT prefer different code shapes.
 */
internal class PureMd5 : MerkleDamgardHash(bigEndianLength = false) {
    override val digestSizeBytes: Int = 16

    /** h0..h3 */
    private val state = IntArray(4)

    /** Message-word scratch space for platforms whose block function uses one */
    private val words = IntArray(MD5_WORDS_SIZE)

    init {
        resetState()
    }

    override fun resetState() {
        state[0] = 0x67452301
        state[1] = 0xefcdab89.toInt()
        state[2] = 0x98badcfe.toInt()
        state[3] = 0x10325476
    }

    override fun processBlocks(input: ByteArray, offset: Int, count: Int): Unit = md5ProcessBlocks(state, words, input, offset, count)

    override fun writeDigest(out: ByteArray) {
        for (i in 0 until 4) out.putIntLe(4 * i, state[i])
    }

    override fun newInstance(): MerkleDamgardHash = PureMd5()

    override val scratch: IntArray get() = words

    override fun copyChainingStateFrom(other: MerkleDamgardHash) {
        (other as PureMd5).state.copyInto(state)
    }
}

/** Size of the message-word scratch array passed to [md5ProcessBlocks]; 0 where it isn't used */
internal expect val MD5_WORDS_SIZE: Int

/**
 * Compresses [count] 64-byte blocks of [input] from [offset] into the MD5 chaining [state] (h0..h3), using [words] (of
 * [MD5_WORDS_SIZE] words) as scratch space
 */
internal expect fun md5ProcessBlocks(state: IntArray, words: IntArray, input: ByteArray, offset: Int, count: Int)
