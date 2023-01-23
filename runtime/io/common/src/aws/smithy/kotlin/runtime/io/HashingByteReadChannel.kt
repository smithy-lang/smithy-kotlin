/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred

/**
 * A channel which hashes data as it is being read
 * @param hash The [HashFunction] to hash data with
 * @param chan the [SdkByteReadChannel] to hash
 * @param deferred the optional [CompletableDeferred] to be completed using the Base64-encoded digest of [chan] after it is exhausted
 */
@InternalApi
public class HashingByteReadChannel(
    private val hash: HashFunction,
    private val chan: SdkByteReadChannel,
    private val deferred: CompletableDeferred<String>? = null
) : SdkByteReadChannel by chan {
    public override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        val bufferedHashingSink = HashingSink(hash, sink).buffer()
        return chan.read(bufferedHashingSink.buffer, limit).also {
            bufferedHashingSink.emit()
            if (it == -1L) {
                deferred?.complete(digest().encodeBase64String())
            }
        }
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}
