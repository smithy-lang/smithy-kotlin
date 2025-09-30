/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CoroutineScope

/**
 * Consume the [SdkSource] and pull the entire contents into memory as a [ByteArray].
 */
@InternalApi
public actual suspend fun SdkSource.readToByteArray(): ByteArray {
    TODO("Not yet implemented")
}

/**
 * Convert the [SdkSource] to an [SdkByteReadChannel]. Content is read from the source and forwarded
 * to the channel.
 * @param coroutineScope the coroutine scope to use to launch a background reader channel responsible for propagating data
 * between source and the returned channel
 */
@InternalApi
public actual fun SdkSource.toSdkByteReadChannel(coroutineScope: CoroutineScope?): SdkByteReadChannel {
    TODO("Not yet implemented")
}
