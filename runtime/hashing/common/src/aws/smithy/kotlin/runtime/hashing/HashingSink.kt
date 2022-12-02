/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.hashing

import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.internal.SdkSinkObserver

/**
 * A sink which hashes data as it is being written to
 * @param hash The [HashFunction] to hash data with
 * @param sink the [SdkSink] to hash
 */
public open class HashingSink(public val hash: HashFunction, sink: SdkSink) : HashFunction by hash, SdkSinkObserver(sink) {
    override fun observe(data: ByteArray, offset: Int, length: Int) {
        update(data, offset, length)
    }
}
