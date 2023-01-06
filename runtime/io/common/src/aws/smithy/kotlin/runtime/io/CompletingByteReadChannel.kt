/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred

/**
 * A channel which uses the digest of an underlying [HashingByteReadChannel] to complete a [CompletableDeferred]
 * @param deferredChecksum The [CompletableDeferred] to be completed when the underlying channel is exhausted
 * @param hashingChannel the [HashingByteReadChannel] which will be digested and used to complete [deferredChecksum]
 */
@InternalApi
public class CompletingByteReadChannel(
    private val deferredChecksum: CompletableDeferred<String>,
    private val hashingChannel: HashingByteReadChannel,
) : SdkByteReadChannel by hashingChannel {
    public override suspend fun read(sink: SdkBuffer, limit: Long): Long =
        hashingChannel.read(sink, limit).also {
            if (hashingChannel.isClosedForRead) {
                deferredChecksum.complete(hashingChannel.digest().encodeBase64String())
            }
        }
}
