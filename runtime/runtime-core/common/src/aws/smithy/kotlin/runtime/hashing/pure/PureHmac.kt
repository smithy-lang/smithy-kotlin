/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import kotlin.experimental.xor

/**
 * HMAC (RFC 2104) over a pure [MerkleDamgardHash]. The inner (key xor ipad) and outer (key xor opad) blocks are
 * compressed once at construction; each [mac] call restores those saved states instead of recompressing them, saving
 * two block compressions per call versus runtime-core's `hmac`. Keys longer than the block size are hashed first.
 *
 * [hash] only selects the algorithm: this class works on fresh instances of its type and never reads, changes or keeps
 * [hash] itself. Not thread-safe: holds mutable per-instance state. It retains key-derived hash state until [wipe].
 */
internal class PureHmac(key: ByteArray, hash: MerkleDamgardHash) {
    private val innerState: MerkleDamgardHash = hash.fresh()
    private val outerState: MerkleDamgardHash = hash.fresh()
    private val work: MerkleDamgardHash = hash.fresh()

    init {
        val blockSize = work.blockSizeBytes
        val hashedKey = if (key.size > blockSize) work.apply { update(key) }.digest() else null
        val blockKey = (hashedKey ?: key).copyOf(blockSize)
        val pad = ByteArray(blockSize)

        for (i in 0 until blockSize) pad[i] = blockKey[i] xor 0x36
        innerState.update(pad)
        for (i in 0 until blockSize) pad[i] = blockKey[i] xor 0x5c
        outerState.update(pad)

        // Best effort: the JVM and Kotlin/Native may still hold copies elsewhere. The saved states' schedule scratch
        // holds the pad words, which would give the key back.
        innerState.wipeScratch()
        outerState.wipeScratch()
        hashedKey?.fill(0)
        blockKey.fill(0)
        pad.fill(0)
    }

    fun mac(message: ByteArray): ByteArray = mac(message, message.size)

    /** HMAC of the first [length] bytes of [message] */
    fun mac(message: ByteArray, length: Int): ByteArray {
        work.restoreFrom(innerState)
        work.update(message, 0, length)
        val innerHash = work.digest()

        work.restoreFrom(outerState)
        work.update(innerHash)
        innerHash.fill(0)
        return work.digest()
    }

    /** Zeroes all key-derived state. The instance can't be used afterwards. */
    fun wipe() {
        innerState.reset()
        outerState.reset()
        work.reset()
    }
}
