/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.io.internal.SdkSourceObserver
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred

/**
 * A source which hashes data as it is being consumed
 * @param hash The [HashFunction] to hash data with
 * @param source the [SdkSource] to hash
 * @param deferred the optional [CompletableDeferred] to be completed using the Base64-encoded digest of [source] after it is exhausted
 */
@InternalApi
public class HashingSource(
    private val hash: HashFunction,
    private val source: SdkSource,
    private val deferred: CompletableDeferred<String>? = null,
) : SdkSourceObserver(source) {

    override fun read(sink: SdkBuffer, limit: Long): Long = super.read(sink, limit).also {
        if (it == -1L) {
            deferred?.complete(digest().encodeBase64String())
        }
    }

    override fun observe(data: ByteArray, offset: Int, length: Int) {
        hash.update(data, offset, length)
    }

    /**
     * Provides the digest as a ByteArray
     * @return a ByteArray representing the contents of the hash
     */
    public fun digest(): ByteArray = hash.digest()
}
