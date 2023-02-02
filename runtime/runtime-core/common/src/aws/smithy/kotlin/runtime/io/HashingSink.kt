/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.io.internal.SdkSinkObserver

/**
 * A sink which hashes data as it is being written to
 * @param hash The [HashFunction] to hash data with
 * @param sink the [SdkSink] to hash
 */
@InternalApi
public class HashingSink(private val hash: HashFunction, sink: SdkSink = SdkSink.blackhole()) : SdkSinkObserver(sink) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        hash.update(data, offset, length)
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}
