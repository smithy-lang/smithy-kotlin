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
public open class HashingSource(public val hash: HashFunction, source: SdkSource) : HashFunction by hash, SdkSourceObserver(source) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        update(data, offset, length)
    }
}
