/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.ByteChannel as KtorByteChannel
import io.ktor.utils.io.ByteReadChannel as KtorByteReadChannel

/**
 * Channel for asynchronous reading and writing sequences of bytes.
 *
 * This is a buffered **single-reader single writer channel**.
 *
 * Read operations can be invoked concurrently with write operations, but multiple reads or multiple writes
 * cannot be invoked concurrently with themselves. Exceptions are [close] and [flush] which can be invoked
 * concurrently with other operations including between themselves at any time.
 */
public interface ByteChannel : ByteReadChannel, ByteWriteChannel

/**
 * Create a buffered channel for asynchronous reading and writing of bytes
 */
public fun ByteChannel(autoFlush: Boolean = true): ByteChannel =
    KtorByteChannel(autoFlush).toSdkChannel()

/**
 * Creates a channel for reading from the given byte array.
 */
public fun ByteReadChannel(
    content: ByteArray,
    offset: Int = 0,
    length: Int = content.size - offset
): ByteReadChannel = KtorByteReadChannel(content, offset, length).toSdkChannel()
