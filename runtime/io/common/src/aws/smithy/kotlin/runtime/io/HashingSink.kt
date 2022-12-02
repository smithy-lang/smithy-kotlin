/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.Crc32
import aws.smithy.kotlin.runtime.hashing.Crc32Base
import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.io.internal.SdkSinkObserver
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * A sink which hashes data as it is being written to
 * @param hash The [HashFunction] to hash data with
 * @param sink the [SdkSink] to hash
 */
@InternalApi
public class HashingSink(private val hash: HashFunction, sink: SdkSink) : SdkSinkObserver(sink) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        hash.update(data, offset, length)
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}

/**
 * A sink which hashes data as it is being written to
 * @param hash The [Crc32Base] function to hash data with. Supported values are [Crc32] and [Crc32c]
 * @param sink the [SdkSink] to hash
 */
@InternalApi
public class CrcSink(sink: SdkSink, private val hash: Crc32Base = Crc32()) : SdkSinkObserver(sink) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        hash.update(data, offset, length)
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()

    /**
     * Provides the digest value as an unsigned integer for the CRC32 function family.
     * @return unsigned integer representing the value of the digest
     */
    public fun digestValue(): UInt = hash.digestValue()
}
