/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * aws-chunked content encoding. Operations on this class can not be invoked concurrently.
 * This class wraps an SdkByteReadChannel. When reads are performed on this class, it will read the wrapped data
 * and return it in aws-chunked content encoding.
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html">SigV4 Streaming</a>
 * @param chan the underlying [SdkByteReadChannel] which will have its data encoded in aws-chunked format
 * @param signer the signer to use to sign chunks and (optionally) chunk trailer
 * @param signingConfig the config to use for signing
 * @param previousSignature the previous signature to use for signing. in most cases, this should be the seed signature
 * @param trailingHeaders the optional trailing headers to include in the final chunk
 */
@InternalApi
public expect class AwsChunkedByteReadChannel public constructor(
    chan: SdkByteReadChannel,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
    trailingHeaders: Headers = Headers.Empty,
) : AbstractAwsChunkedByteReadChannel
