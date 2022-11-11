/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import java.nio.ByteBuffer

internal actual class AwsChunked actual constructor(
    chan: SdkByteReadChannel,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
    trailingHeaders: Headers,
) : AbstractAwsChunked(chan, signer, signingConfig, previousSignature, trailingHeaders) {

    /**
     * Read all the available bytes into [sink], up to the [sink]'s limit.
     * After reading is complete, flips the buffer to ready the content for consumption.
     * @param sink the [ByteBuffer] to read the bytes into
     * @return an integer representing the number of bytes written to [sink]
     */
    override suspend fun readAvailable(sink: ByteBuffer): Int {
        if (chunk == null || chunkOffset >= chunk!!.size) {
            chunk = getNextChunk()
            if (chunk == null) {
                return -1
            }
        }

        var bytesWritten = 0
        while (chunkOffset < chunk!!.size && sink.position() != sink.limit()) {
            val numBytesToWrite = minOf(sink.limit(), chunk!!.size - chunkOffset)
            val bytes = chunk!!.slice(chunkOffset until chunkOffset + numBytesToWrite).toByteArray()
            sink.put(bytes)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // if we've exhausted the current chunk, exit without suspending for a new one
            if (chunk == null || chunkOffset >= chunk!!.size) { break }
        }

        sink.flip()
        return bytesWritten
    }
}
