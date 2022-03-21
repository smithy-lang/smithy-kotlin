/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.ByteBuffer

// streaming buffer size. The SDK stream is copied in chunks of this size to the output Ktor ByteReadChannel.
// Too small and we risk too many context switches, too large and we waste memory and (if large enough) become
// CPU cache unfriendly. Disk read/write >> main memory copies so we _shouldn't_ be the bottleneck.
// 4K has historically been chosen for a number of reasons including default disk cluster size as well as default
// OS page size.
//
// NOTE: This says nothing of the underlying outgoing buffer sizes used in Ktor to actually put data on the wire!!
private const val BUFFER_SIZE = 4096

internal actual suspend fun forwardSource(dst: ByteChannel, source: SdkByteReadChannel) {
    val buffer = ByteBuffer.allocate(BUFFER_SIZE)
    while (!source.isClosedForRead) {
        // fill the buffer by reading chunks from the underlying source
        while (source.readAvailable(buffer) != -1 && buffer.remaining() > 0) {}
        buffer.flip()

        // propagate it to the channel
        dst.writeFully(buffer)
        dst.flush()
        buffer.clear()
    }
}

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
