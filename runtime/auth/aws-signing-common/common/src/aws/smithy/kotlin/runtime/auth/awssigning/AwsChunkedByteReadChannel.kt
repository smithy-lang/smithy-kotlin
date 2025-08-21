/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awssigning.internal.StreamChunker
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
    private val chunker = StreamChunker(delegate.adapt(), signer, signingConfig, previousSignature, trailingHeaders)

    override suspend fun read(sink: SdkBuffer, limit: Long): Long {
        require(limit >= 0L) { "Invalid limit ($limit) must be >= 0L" }
        return chunker.readAndMaybeWrite(sink, limit)
    }
}

private fun SdkByteReadChannel.adapt(): StreamChunker.Adapter = object : StreamChunker.Adapter {
    val delegate = this@adapt

    override val eof: Boolean get() = delegate.isClosedForRead
    override suspend fun read(buffer: SdkBuffer, limit: Long): Long = delegate.read(buffer, limit)
}
