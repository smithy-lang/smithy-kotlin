/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

/**
 * A collection of bytes in memory. Moving data from one buffer to another is fast.
 *
 * **Thread Safety** Buffer is NOT thread safe and should not be shared between threads without
 * external synchronization.
 */
public expect class SdkBuffer : SdkBufferedSource, SdkBufferedSink {
    public val size: Long

    public constructor()

    internal val inner: okio.Buffer

    internal constructor(buffer: okio.Buffer)

    override val buffer: SdkBuffer
}
