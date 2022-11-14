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
        if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }

        var bytesWritten = 0
        val bytes = ByteArray(limit)
        while (bytesWritten != limit) {
            val numBytesToWrite: Int = minOf(limit - bytesWritten, chunk!!.size - chunkOffset)

            chunk!!.copyInto(bytes, bytesWritten, chunkOffset, chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // read a new chunk. this handles the case where we consumed the whole chunk but still have not sent `limit` bytes
            if (chunkOffset >= chunk!!.size) {
                chunk = getNextChunk()
                if (chunk == null) { break } // we've exhausted all remaining bytes
            }
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

        if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }

        var bytesWritten = 0
        while (bytesWritten != length) {
            if (chunk == null) { throw RuntimeException("Invalid read: unable to fully read $length bytes. missing ${length - bytesWritten} bytes.") }

            val numBytesToWrite: Int = minOf(length, chunk!!.size - chunkOffset)

            chunk!!.copyInto(sink, offset + bytesWritten, chunkOffset, chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            if (chunkOffset >= chunk!!.size) {
                chunk = getNextChunk()
            }
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

        var bytesWritten = 0

        // make sure the current chunk is valid -- suspend and read a new chunk if not valid
        if (chunk == null || chunkOffset >= chunk!!.size) {
            chunk = getNextChunk()
            if (chunk == null) { return bytesWritten }
        }

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
     * Read the next chunk of data and add hex-formatted chunk size and chunk signature to the front
     * This function assumes that the previous chunk has been fully consumed and there is no remaining data, because the
     * previous chunk will be overwritten with new data.
     * The chunk structure is: `string(IntHexBase(chunk-size)) + ";chunk-signature=" + signature + \r\n + chunk-data + \r\n`
     * @return an aws-chunked encoded ByteArray with the hex-formatted chunk size, chunk signature, and chunk data
     * (in that order), ready to send on the wire. the return value will be null when the chunk data has been exhausted.
     */
    suspend fun getNextChunk(): ByteArray? {
        val chunk = if (chan.isClosedForRead && hasLastChunkBeenSent) {
            null
        } else if (chan.isClosedForRead && !hasLastChunkBeenSent) {
            hasLastChunkBeenSent = true

            when (trailingHeaders) {
                Headers.Empty -> getChunk(byteArrayOf())
                else -> getChunk(byteArrayOf()) + getTrailingHeadersChunk(trailingHeaders)
            }
        } else {
            getChunk()
        }

        chunkOffset = 0
        return chunk?.plus("\r\n".encodeToByteArray()) // terminating CRLF to signal end of chunk
    }

    /**
     * Get an aws-chunked encoding of [data]
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

        chunkOffset = 0
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
