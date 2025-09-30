/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Wraps the SdkByteReadChannel so that it compresses into gzip format with each read.
 */
@InternalApi
public actual class GzipByteReadChannel actual constructor(channel: SdkByteReadChannel) : SdkByteReadChannel {
    actual override val availableForRead: Int
        get() = TODO("Not yet implemented")
    actual override val isClosedForRead: Boolean
        get() = TODO("Not yet implemented")
    actual override val isClosedForWrite: Boolean
        get() = TODO("Not yet implemented")
    actual override val closedCause: Throwable?
        get() = TODO("Not yet implemented")

    actual override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        TODO("Not yet implemented")
    }

    actual override fun cancel(cause: Throwable?): Boolean {
        TODO("Not yet implemented")
    }
}
