/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.InternalApi

// FIXME - going to have to handle this for SdkSource now as well

/**
 * Chunk size used by Transfer-Encoding `aws-chunked`
 */
public const val CHUNK_SIZE_BYTES: Int = 65_536

/**
 * aws-chunked content encoding. Operations on this class can not be invoked concurrently.
 * This class wraps an SdkByteReadChannel. When reads are performed on this class, it will read the wrapped data
 * and return it in aws-chunked content encoding.
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html">SigV4 Streaming</a>
 * @param delegate the underlying [SdkByteReadChannel] which will have its data encoded in aws-chunked format
 * @param signer the signer to use to sign chunks and (optionally) chunk trailer
 * @param signingConfig the config to use for signing
 * @param previousSignature the previous signature to use for signing. in most cases, this should be the seed signature
 * @param trailingHeaders the optional trailing headers to include in the final chunk
 */
@InternalApi
public class AwsChunkedByteReadChannel(
    private val delegate: SdkByteReadChannel,
    private val signer: AwsSigner,
    private val signingConfig: AwsSigningConfig,
    private var previousSignature: ByteArray,
    private val trailingHeaders: Headers = Headers.Empty,
) : SdkByteReadChannel by delegate {
    override val isClosedForRead: Boolean
        get() = chunk.size == 0L && hasLastChunkBeenSent && delegate.isClosedForRead

    override val availableForRead: Int
        get() = chunk.size.toInt() + delegate.availableForRead

    private val chunk = SdkBuffer()
    private var hasLastChunkBeenSent = false

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L) { "Invalid limit ($limit) must be >= 0L" }
        if (!ensureValidChunk()) return -1L
        return chunk.read(sink, limit)
    }

    /**
     * Ensures that the internal [chunk] is valid for reading. If it's not valid, try to load the next chunk. Note that
     * this function will suspend until the whole chunk has been loaded.
     *
     * @return true if the [chunk] is valid for reading, false if it's invalid (chunk data is exhausted)
     */
    private suspend fun ensureValidChunk(): Boolean {
        // check if the current chunk is still valid
        if (chunk.size > 0L) return true

        // // if not, try to fetch a new chunk
        // val nextChunk = if (delegate.isClosedForRead && hasLastChunkBeenSent) {
        //     null
        // } else if (delegate.isClosedForRead && !hasLastChunkBeenSent) {
        //     hasLastChunkBeenSent = true
        //     // empty chunk
        //     val lastChunk = checkNotNull(getSignedChunk(SdkBuffer()))
        //     if (!trailingHeaders.isEmpty()) {
        //         val trailingHeaderChunk = getTrailingHeadersChunk(trailingHeaders)
        //         lastChunk.writeAll(trailingHeaderChunk)
        //     }
        //     lastChunk
        // } else {
        //     getSignedChunk()
        // }

        val nextChunk = when {
            delegate.isClosedForRead && hasLastChunkBeenSent -> null
            else -> {
                var next = getSignedChunk()
                if (next == null) {
                    check(delegate.isClosedForRead) { "Expected underlying channel to be closed" }
                    next = getFinalChunk()
                    hasLastChunkBeenSent = true
                }
                next
            }
        }

        nextChunk?.writeUtf8("\r\n") // terminating CRLF to signal end of chunk

        // transfer all segments to the working chunk
        nextChunk?.let { chunk.writeAll(it) }

        return chunk.size > 0L
    }

    /**
     * Get the last chunk that will be sent which consists of an empty signed chunk + any
     * trailers
     */
    private suspend fun getFinalChunk(): SdkBuffer {
        // empty chunk
        val lastChunk = checkNotNull(getSignedChunk(SdkBuffer()))

        // + any trailers
        if (!trailingHeaders.isEmpty()) {
            val trailingHeaderChunk = getTrailingHeadersChunk(trailingHeaders)
            lastChunk.writeAll(trailingHeaderChunk)
        }
        return lastChunk
    }

    /**
     * Get an aws-chunked encoding of [data].
     * If [data] is not set, read the next chunk from [delegate] and add hex-formatted chunk size and chunk signature to the front.
     * Note that this function will suspend until the whole chunk has been read.
     * The chunk structure is: `string(IntHexBase(chunk-size)) + ";chunk-signature=" + signature + \r\n + chunk-data + \r\n`
     *
     * @param data the ByteArray of data which will be encoded to aws-chunked. if not provided, will default to
     * reading up to [CHUNK_SIZE_BYTES] from [delegate].
     * @return a ByteArray containing the chunked data or null if no data is available
     */
    private suspend fun getSignedChunk(data: SdkBuffer? = null): SdkBuffer? {
        val bodyBuffer = if (data == null) {
            val sink = SdkBuffer()
            when (delegate.read(sink, CHUNK_SIZE_BYTES.toLong())) {
                -1L -> null
                else -> sink
            }
        } else {
            data
        }

        val chunkBody = bodyBuffer?.readByteArray() ?: return null

        // signer takes a ByteArray unfortunately...
        val chunkSignature = signer.signChunk(chunkBody, previousSignature, signingConfig).signature
        previousSignature = chunkSignature

        val signedChunk = SdkBuffer()

        // headers
        signedChunk.apply {
            writeUtf8(chunkBody.size.toString(16))
            writeUtf8(";")
            writeUtf8("chunk-signature=")
            write(chunkSignature)
            writeUtf8("\r\n")
        }

        // append the body
        signedChunk.write(chunkBody)

        return signedChunk
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
    private suspend fun getTrailingHeadersChunk(trailingHeaders: Headers): SdkBuffer {
        val trailerSignature = signer.signChunkTrailer(trailingHeaders, previousSignature, signingConfig).signature
        previousSignature = trailerSignature

        val trailerBody = SdkBuffer()
        trailerBody.writeTrailers(trailingHeaders, trailerSignature.decodeToString())
        return trailerBody
    }
}

private fun SdkBuffer.writeTrailers(
    trailers: Headers,
    signature: String,
) {
    trailers
        .entries()
        .sortedBy { entry -> entry.key.lowercase() }
        .forEach { entry ->
            writeUtf8(entry.key)
            writeUtf8(":")
            writeUtf8(entry.value.joinToString(",") { v -> v.trim() })
            writeUtf8("\r\n")
        }
    writeUtf8("x-amz-trailer-signature:${signature}\r\n")
}
