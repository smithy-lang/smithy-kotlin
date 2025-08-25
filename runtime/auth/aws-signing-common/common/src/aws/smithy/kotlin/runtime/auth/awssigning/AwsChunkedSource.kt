/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awssigning.internal.AwsChunkedReader
import aws.smithy.kotlin.runtime.http.DeferredHeaders
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.buffer
import kotlinx.coroutines.runBlocking

/**
 * aws-chunked content encoding.
 * This class wraps an [SdkSource]. When reads are performed on this class, it will read the wrapped data
 * and return it in aws-chunked content encoding.
 *
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html">SigV4 Streaming</a>
 * @param delegate the underlying [SdkSource] which will have its data encoded in aws-chunked format
 * @param signer the signer to use to sign chunks and (optionally) chunk trailer
 * @param signingConfig the config to use for signing
 * @param previousSignature the previous signature to use for signing. in most cases, this should be the seed signature
 * @param trailingHeaders the optional trailing headers to include in the final chunk
 */
@InternalApi
public class AwsChunkedSource(
    private val delegate: SdkSource,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
    trailingHeaders: DeferredHeaders = DeferredHeaders.Empty,
) : SdkSource {
    private val chunkReader = AwsChunkedReader(
        delegate.asStream(),
        signer,
        signingConfig,
        previousSignature,
        trailingHeaders,
    )

    override fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L) { "Invalid limit ($limit) must be >= 0L" }
        // COROUTINE SAFETY: runBlocking is allowed here because SdkSource is a synchronous blocking interface
        val isChunkValid = runBlocking {
            chunkReader.ensureValidChunk()
        }
        if (!isChunkValid) return -1L
        return chunkReader.chunk.read(sink, limit)
    }

    override fun close() {
        delegate.close()
    }
}

private fun SdkSource.asStream(): AwsChunkedReader.Stream = object : AwsChunkedReader.Stream {
    private val delegate = this@asStream.buffer()

    override fun isClosedForRead(): Boolean =
        delegate.exhausted()

    override suspend fun read(sink: SdkBuffer, limit: Long): Long =
        delegate.read(sink, limit)
}
