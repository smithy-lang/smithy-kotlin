/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

internal const val CHUNK_SIZE_BYTES: Int = 65536

internal abstract class AbstractAwsChunkedByteReadChannel(
    private val chan: SdkByteReadChannel,
    private val signer: AwsSigner,
    private val signingConfig: AwsSigningConfig,
    private var previousSignature: ByteArray,
    private var trailingHeaders: Headers = Headers.Empty,
) : SdkByteReadChannel by chan {
    override val isClosedForRead: Boolean
        get() = chan.isClosedForRead && (chunk == null || chunkOffset >= chunk!!.size)

    var chunk: ByteArray? = null
    var chunkOffset: Int = 0
    var hasLastChunkBeenSent: Boolean = false

    /**
     * Returns all the bytes remaining in the underlying data source, up to [limit].
     * @return a [ByteArray] containing at most [limit] bytes. it may contain fewer if there are less than [limit] bytes
     * remaining in the data source.
     */
    override suspend fun readRemaining(limit: Int): ByteArray {
        if (!ensureValidChunk()) {
            return byteArrayOf()
        }

        var bytesWritten = 0
        val bytes = ByteArray(limit)

        while (bytesWritten != limit) {
            val numBytesToWrite: Int = minOf(limit - bytesWritten, chunk!!.size - chunkOffset)

            chunk!!.copyInto(bytes, bytesWritten, chunkOffset, chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // read a new chunk. this handles the case where we consumed the whole chunk but still have not sent `limit` bytes
            if (!ensureValidChunk()) { break }
        }

        return bytes.sliceArray(0 until bytesWritten)
    }

    /**
     * Writes [length] bytes to [sink], starting [offset] bytes from the beginning. If [length] bytes are not available in
     * the source data, the call will fail with an [IllegalArgumentException].
     *
     * @param sink the destination [ByteArray] to write to
     * @param offset the number of bytes in [sink] to skip before beginning to write
     * @param length the number of bytes to write to [sink]
     * @throws IllegalArgumentException when illegal [offset] and [length] arguments are passed
     * @throws RuntimeException when the source data is exhausted before [length] bytes are written to [sink]
     */
    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "Invalid read: offset must be positive:  $offset" }
        require(offset + length <= sink.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${sink.size}" }
        if (length == 0) return

        var bytesWritten = 0

        while (bytesWritten != length) {
            if (!ensureValidChunk()) {
                throw RuntimeException("Invalid read: unable to fully read $length bytes. missing ${length - bytesWritten} bytes.")
            }

            val numBytesToWrite: Int = minOf(length, chunk!!.size - chunkOffset)

            chunk!!.copyInto(sink, offset + bytesWritten, chunkOffset, chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite
        }
    }

    /**
     * Writes up to [length] bytes to [sink], starting [offset] bytes from the beginning.
     * Returns when [length] bytes or the number of available bytes have been written, whichever is lower.
     *
     * This function will read *at most* one chunk of data into the [sink]. Successive calls will be required to read additional chunks.
     * This is done because the function promises to not suspend unless there are zero bytes currently available,
     * and we are unable to poll the underlying data source to see if there is a whole chunk available.
     *
     * @param sink the [ByteArray] to write the data to
     * @param offset the number of bytes to skip from the beginning of the chunk
     * @param length the maximum number of bytes to write to [sink]. the actual number of bytes written may be fewer if
     * there are less immediately available.
     * @throws IllegalArgumentException when illegal [offset] and [length] arguments are passed
     * @return an [Int] representing the number of bytes written
     */
    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0) { "Invalid read: offset must be positive:  $offset" }
        require(offset + length <= sink.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${sink.size}" }
        if (length == 0 || !ensureValidChunk()) {
            return 0
        }

        var bytesWritten = 0

        while (bytesWritten != length) {
            val numBytesToWrite = minOf(length, chunk!!.size - chunkOffset)

            chunk!!.copyInto(sink, offset + bytesWritten, chunkOffset, chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // if we've exhausted the current chunk, exit without suspending for a new one
            if (chunkOffset >= chunk!!.size) { break }
        }

        return bytesWritten
    }

    /**
     * Ensures that the internal [chunk] is valid for reading. If it's not valid, try to load the next chunk.
     * @return true if the [chunk] is valid for reading, false if it's invalid (chunk data is exhausted)
     */
    suspend fun ensureValidChunk(): Boolean {
        // check if the current chunk is still valid
        if (chunk != null && chunkOffset < chunk!!.size) { return true }

        // if not, try to fetch a new chunk
        val nextChunk = if (chan.isClosedForRead && hasLastChunkBeenSent) {
            null
        } else if (chan.isClosedForRead && !hasLastChunkBeenSent) {
            hasLastChunkBeenSent = true
            getChunk(byteArrayOf()) + if (!trailingHeaders.isEmpty()) { getTrailingHeadersChunk(trailingHeaders) } else byteArrayOf()
        } else {
            getChunk()
        }

        chunkOffset = 0
        chunk = nextChunk?.plus("\r\n".encodeToByteArray()) // terminating CRLF to signal end of chunk
        return (chunk != null)
    }

    /**
     * Get an aws-chunked encoding of [data].
     * If [data] is not set, read the next chunk from [chan] and add hex-formatted chunk size and chunk signature to the front.
     * The chunk structure is: `string(IntHexBase(chunk-size)) + ";chunk-signature=" + signature + \r\n + chunk-data + \r\n`
     *
     * @param data the ByteArray of data which will be encoded to aws-chunked. if not provided, will default to
     * reading up to [CHUNK_SIZE_BYTES] from [chan].
     * @return a ByteArray containing the chunked data
     */
    private suspend fun getChunk(data: ByteArray? = null): ByteArray {
        val chunkBody = data ?: chan.readRemaining(CHUNK_SIZE_BYTES)

        val chunkSignature = signer.signChunk(chunkBody, previousSignature, signingConfig).signature
        previousSignature = chunkSignature

        val chunkHeader = buildString {
            append(chunkBody.size.toString(16))
            append(";")
            append("chunk-signature=")
            append(chunkSignature.decodeToString())
            append("\r\n")
        }.encodeToByteArray()

        return chunkHeader + chunkBody
    }

    /**
     * Get the trailing headers chunk. The grammar for trailing headers is:
     * trailing-header-A:value CRLF
     * trailing-header-B:value CRLF
     * ...
     * x-amz-trailer-signature:signature_value CRLF
     *
     * @param trailingHeaders a list of [Headers] which will be sent
     * @return a [ByteArray] containing the trailing headers in aws-chunked encoding, ready to send on the wire
     */
    private suspend fun getTrailingHeadersChunk(trailingHeaders: Headers): ByteArray {
        var trailerBody = trailingHeaders.entries().map {
                entry ->
            buildString {
                append(entry.key)
                append(":")
                append(entry.value.joinToString(","))
                append("\r\n")
            }.encodeToByteArray()
        }.reduce { acc, bytes -> acc + bytes }

        val trailerSignature = signer.signChunkTrailer(trailerBody, previousSignature, signingConfig).signature
        previousSignature = trailerSignature

        trailerBody += "x-amz-trailer-signature:${trailerSignature.decodeToString()}\r\n".encodeToByteArray()

        chunkOffset = 0
        return trailerBody
    }
}
