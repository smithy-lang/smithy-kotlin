/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awssigning.internal.AwsChunkedReader
import aws.smithy.kotlin.runtime.http.DeferredHeaders
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

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
    private val trailingHeaders: DeferredHeaders = DeferredHeaders.Empty,
) : SdkByteReadChannel by delegate {

    private val chunkReader = AwsChunkedReader(
        delegate.asStream(),
        signer,
        signingConfig,
        previousSignature,
        trailingHeaders,
    )

    override val isClosedForRead: Boolean
        get() = chunkReader.chunk.size == 0L && chunkReader.hasLastChunkBeenSent && delegate.isClosedForRead

    override val availableForRead: Int
        get() = chunkReader.chunk.size.toInt() + delegate.availableForRead

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L) { "Invalid limit ($limit) must be >= 0L" }
        if (!chunkReader.ensureValidChunk()) return -1L
        return chunkReader.chunk.read(sink, limit)
    }
}

private fun SdkByteReadChannel.asStream(): AwsChunkedReader.Stream = object : AwsChunkedReader.Stream {
    private val delegate = this@asStream

    override fun isClosedForRead(): Boolean =
        delegate.isClosedForRead

    override suspend fun read(sink: SdkBuffer, limit: Long): Long =
        delegate.read(sink, limit)
}
