/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.io.internal.commonPeek
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * A channel which hashes data as it is being read
 * @param hash The [HashFunction] to hash data with
 * @param chan the [SdkByteReadChannel] to hash
 */
@InternalApi
public class HashingByteReadChannel(private val hash: HashFunction, private val chan: SdkByteReadChannel) : SdkByteReadChannel by chan {
    public override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        val buffer = SdkBuffer()

        val rc = chan.read(buffer, limit)
        if (rc == -1L) { return rc } // got 0 bytes, no need to hash anything

        val dataToHash = buffer.commonPeek().readByteArray()
        hash.update(dataToHash)

        return buffer.read(sink, rc)
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}
