/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

/**
 * Pure-Kotlin SHA-1 (FIPS 180-4). Compresses all whole blocks of one update in a single call. The block function is
 * platform-specific ([sha1ProcessBlocks]): native and the JVM's JIT prefer different code shapes.
 */
internal class PureSha1 : MerkleDamgardHash(bigEndianLength = true) {
    override val digestSizeBytes: Int = 20

    /** h0..h4 */
    private val state = IntArray(5)

    /** Schedule scratch space for platforms whose block function uses one */
    private val schedule = IntArray(SHA1_SCHEDULE_SIZE)

    init {
        resetState()
    }

    override fun resetState() {
        state[0] = 0x67452301
        state[1] = 0xefcdab89.toInt()
        state[2] = 0x98badcfe.toInt()
        state[3] = 0x10325476
        state[4] = 0xc3d2e1f0.toInt()
    }

    override fun processBlocks(input: ByteArray, offset: Int, count: Int): Unit = sha1ProcessBlocks(state, schedule, input, offset, count)

    override fun writeDigest(out: ByteArray) {
        for (i in 0 until 5) out.putIntBe(4 * i, state[i])
    }

    override fun newInstance(): MerkleDamgardHash = PureSha1()

    override val scratch: IntArray get() = schedule

    override fun copyChainingStateFrom(other: MerkleDamgardHash) {
        (other as PureSha1).state.copyInto(state)
    }
}

internal const val SHA1_K3: Int = 0x8f1bbcdc.toInt()
internal const val SHA1_K4: Int = 0xca62c1d6.toInt()

/** Size of the schedule scratch array passed to [sha1ProcessBlocks]; 0 where it isn't used */
internal expect val SHA1_SCHEDULE_SIZE: Int

/**
 * Compresses [count] 64-byte blocks of [input] from [offset] into the SHA-1 chaining [state] (h0..h4), using
 * [schedule] (of [SHA1_SCHEDULE_SIZE] words) as scratch space
 */
internal expect fun sha1ProcessBlocks(state: IntArray, schedule: IntArray, input: ByteArray, offset: Int, count: Int)
