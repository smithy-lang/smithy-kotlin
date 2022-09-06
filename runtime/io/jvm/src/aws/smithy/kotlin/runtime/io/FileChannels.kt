/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Path
import io.ktor.util.cio.readChannel as cioReadChannel
import io.ktor.util.cio.writeChannel as cioWriteChannel

/**
 * Open a read channel for file and launch a coroutine to fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * NOTE: Always runs on [Dispatchers.IO]
 */
@InternalApi
public fun File.readChannel(
    start: Long = 0,
    endInclusive: Long = -1,
): SdkByteReadChannel = cioReadChannel(start, endInclusive).toSdkChannel()

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
 * NOTE: Always runs on [Dispatchers.IO]
 */
@InternalApi
public fun File.writeChannel(): SdkByteWriteChannel = cioWriteChannel().toSdkChannel()
