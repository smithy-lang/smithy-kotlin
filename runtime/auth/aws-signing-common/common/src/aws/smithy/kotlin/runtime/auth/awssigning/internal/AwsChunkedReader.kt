/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkBuffer

/**
 * Common implementation of aws-chunked content encoding. Operations on this class can not be invoked concurrently.
 * This class wraps a [Stream] which actually provides the raw bytes.
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html">SigV4 Streaming</a>
 * @param stream the underlying IO abstraction which will have its data encoded in aws-chunked format
 * @param signer the signer to use to sign chunks and (optionally) chunk trailer
 * @param signingConfig the config to use for signing
 * @param previousSignature the previous signature to use for signing. in most cases, this should be the seed signature
 * @param trailingHeaders the optional trailing headers to include in the final chunk
 */
internal class AwsChunkedReader(
    private val stream: Stream,
    private val signer: AwsSigner,
    private val signingConfig: AwsSigningConfig,
    private var previousSignature: ByteArray,
    private val trailingHeaders: Headers = Headers.Empty,
) {

    /**
     * Common interface abstracting over [SdkSource] and [SdkByteReadChannel]
     */
    internal interface Stream {
        fun isClosedForRead(): Boolean

        /**
         * Read data from the underlying IO source.
         * NOTE: Implementations may or may not suspend/block. The suspend coloring of this function
         * is to gloss over differences between underlying IO abstractions and share aws-chunked encoding
         * internals.
         */
        suspend fun read(sink: SdkBuffer, limit: Long): Long
    }

    /**
     * The current chunk to read from
     */
    internal val chunk: SdkBuffer = SdkBuffer()

    /**
     * Flag indicating if the last chunk (empty + trailers) has been sent
     */
    internal var hasLastChunkBeenSent: Boolean = false

    /**
     * Ensures that the internal [chunk] is valid for reading. If it's not valid, try to load the next chunk. Note that
     * this function will suspend until the whole chunk has been loaded.
     *
     * @return true if the [chunk] is valid for reading, false if it's invalid (chunk data is exhausted)
     */
    internal suspend fun ensureValidChunk(): Boolean {
        // check if the current chunk is still valid
        if (chunk.size > 0L) return true

        // if not, try to fetch a new chunk
        val nextChunk = when {
            stream.isClosedForRead() && hasLastChunkBeenSent -> null
            else -> {
                var next = getSignedChunk()
                if (next == null) {
                    check(stream.isClosedForRead()) { "Expected underlying reader to be closed" }
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
     * Note that this function will suspend until the whole chunk has been read OR the channel is exhausted.
     * The chunk structure is: `string(IntHexBase(chunk-size)) + ";chunk-signature=" + signature + \r\n + chunk-data + \r\n`
     *
     * @param data the data which will be encoded to aws-chunked. if not provided, will default to
     * reading up to [CHUNK_SIZE_BYTES] from [delegate].
     * @return a buffer containing the chunked data or null if no data is available (channel is closed)
     */
    private suspend fun getSignedChunk(data: SdkBuffer? = null): SdkBuffer? {
        val bodyBuffer = if (data == null) {
            val sink = SdkBuffer()

            // fill up to chunk size bytes
            var remaining = CHUNK_SIZE_BYTES.toLong()
            while (remaining > 0L) {
                val rc = stream.read(sink, remaining)
                if (rc == -1L) break
                remaining -= rc
            }
            when (sink.size) {
                0L -> null // delegate closed without reading any data
                else -> sink
            }
        } else {
            data
        }

        // signer takes a ByteArray unfortunately...
        val chunkBody = bodyBuffer?.readByteArray() ?: return null

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
     * @return a [SdkBuffer] containing the trailing headers in aws-chunked encoding, ready to send on the wire
     */
    private suspend fun getTrailingHeadersChunk(trailingHeaders: Headers): SdkBuffer {
        val trailerSignature = signer.signChunkTrailer(trailingHeaders, previousSignature, signingConfig).signature
        previousSignature = trailerSignature

        val trailerBody = SdkBuffer()
        trailerBody.writeTrailers(trailingHeaders, trailerSignature.decodeToString())
        return trailerBody
    }
}
