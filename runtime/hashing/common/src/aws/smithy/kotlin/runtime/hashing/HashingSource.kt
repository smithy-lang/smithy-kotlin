/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.internal.SdkSourceObserver

/**
 * A source which hashes data as it is being consumed
 * @param hash The [HashFunction] to hash data with
 * @param source the [SdkSource] to hash
 */
public class HashingSource(private val hash: HashFunction, source: SdkSource) : SdkSourceObserver(source) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        hash.update(data, offset, length)
    }

    /**
     * Provides the digest value as an unsigned integer for the CRC32 function family.
     * @return unsigned integer representing the value of the digest, if the [HashFunction] is [Crc32] or [Crc32c], and null otherwise.
     */
    public fun digestValue() : UInt? = (hash as Crc32?)?.digestValue()
}
