/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String
import kotlinx.coroutines.CompletableDeferred

/**
 * An [SdkSource] which uses the digest of an underlying [HashingSource] to complete a [CompletableDeferred]
 * @param deferredChecksum The [CompletableDeferred] to be completed when the underlying source is exhausted
 * @param source the [HashingSource] which will be digested and used to complete [deferredChecksum]
 */
@InternalApi
public class CompletingSource(private val deferredChecksum: CompletableDeferred<String>, private val source: HashingSource) : SdkSource by source {
    public override fun read(sink: SdkBuffer, limit: Long): Long = source.read(sink, limit).also {
        if (it == -1L) {
            deferredChecksum.complete(source.digest().encodeBase64String())
        }
    }
}
