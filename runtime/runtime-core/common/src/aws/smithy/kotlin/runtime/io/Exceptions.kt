/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

public expect open class IOException(message: String?, cause: Throwable?) : Exception {
    public constructor()
    public constructor(message: String?)
}

public expect open class EOFException(message: String?) : IOException {
    public constructor()
}

/**
 * Indicates attempt to write on a closed channel (i.e. [SdkByteWriteChannel.isClosedForWrite] == true)
 * that was closed without a cause. A _failed_ channel rethrows the original [SdkByteWriteChannel.close] cause
 * exception on send attempts.
 */
public class ClosedWriteChannelException(message: String? = null) : IOException(message)
