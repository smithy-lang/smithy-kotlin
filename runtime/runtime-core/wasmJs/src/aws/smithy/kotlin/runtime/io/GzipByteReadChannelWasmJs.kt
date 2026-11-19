/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public actual class GzipByteReadChannel actual constructor(public val channel: SdkByteReadChannel) : SdkByteReadChannel {
    actual override val availableForRead: Int
        get() = TODO("Not yet implemented on wasmJs: gzip")

    actual override val isClosedForRead: Boolean
        get() = channel.isClosedForRead

    actual override val isClosedForWrite: Boolean
        get() = channel.isClosedForWrite

    actual override val closedCause: Throwable?
        get() = channel.closedCause

    actual override suspend fun read(sink: SdkBuffer, limit: Long): Long = TODO("Not yet implemented on wasmJs: gzip")

    actual override fun cancel(cause: Throwable?): Boolean = channel.cancel(cause)
}
