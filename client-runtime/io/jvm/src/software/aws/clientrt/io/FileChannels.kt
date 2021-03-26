/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import kotlinx.coroutines.Dispatchers
import software.aws.clientrt.util.InternalApi
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import io.ktor.util.cio.readChannel as cioReadChannel
import io.ktor.util.cio.writeChannel as cioWriteChannel

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * This is why [coroutineContext] should have [Dispatchers.IO] or
 * a coroutine dispatcher that is properly configured for blocking IO.
 */
@InternalApi
public fun File.readChannel(
    start: Long = 0,
    endInclusive: Long = -1,
    coroutineContext: CoroutineContext = Dispatchers.IO
): SdkByteReadChannel = cioReadChannel(start, endInclusive, coroutineContext).toSdkChannel()

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
@InternalApi
public fun Path.readChannel(start: Long, endInclusive: Long): SdkByteReadChannel =
    toFile().readChannel(start, endInclusive)

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code
 */
@InternalApi
public fun Path.readChannel(): SdkByteReadChannel = toFile().readChannel()

/**
 * Open a write channel for the file and launch a coroutine to read from it.
 * Please note that file writing is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * This is why [coroutineContext] should have [Dispatchers.IO] or
 * a coroutine dispatcher that is properly configured for blocking IO.
 */
@InternalApi
public fun File.writeChannel(
    coroutineContext: CoroutineContext = Dispatchers.IO
): SdkByteWriteChannel = cioWriteChannel(coroutineContext).toSdkChannel()
