/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.hashing.HashFunction

/**
 * A channel which hashes data as it is being read
 * @param hash The [HashFunction] to hash data with
 * @param chan the [SdkByteReadChannel] to hash
 */
@InternalApi
public class HashingByteReadChannel(
    private val hash: HashFunction,
    private val chan: SdkByteReadChannel,
) : SdkByteReadChannel by chan {
    public override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        val bufferedHashingSink = HashingSink(hash, sink).buffer()
        return chan.read(bufferedHashingSink.buffer, limit).also {
            bufferedHashingSink.emit()
        }
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}
