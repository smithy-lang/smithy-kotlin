/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Wraps the SdkByteReadChannel so that it compresses into gzip format with each read.
 */
@InternalApi
public actual class GzipByteReadChannel actual constructor(channel: SdkByteReadChannel) : SdkByteReadChannel {
    override val availableForRead: Int
        get() = TODO("Not yet implemented")
    override val isClosedForRead: Boolean
        get() = TODO("Not yet implemented")
    override val isClosedForWrite: Boolean
        get() = TODO("Not yet implemented")
    override val closedCause: Throwable?
        get() = TODO("Not yet implemented")

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        TODO("Not yet implemented")
    }

    override fun cancel(cause: Throwable?): Boolean {
        TODO("Not yet implemented")
    }
}