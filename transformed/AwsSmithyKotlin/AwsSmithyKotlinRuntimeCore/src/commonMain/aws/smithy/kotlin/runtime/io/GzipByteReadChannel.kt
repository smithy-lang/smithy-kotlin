/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

/**
 * Wraps the SdkByteReadChannel so that it compresses into gzip format with each read.
 */
public expect class GzipByteReadChannel(channel: SdkByteReadChannel) : SdkByteReadChannel {
    override suspend fun read(sink: SdkBuffer, limit: Long): Long
    override fun cancel(cause: Throwable?): Boolean
    override val availableForRead: Int
    override val isClosedForWrite: Boolean
    override val isClosedForRead: Boolean
    override val closedCause: Throwable?
}
