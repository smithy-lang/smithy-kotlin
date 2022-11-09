/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

/**
 * AwsChunked Encoding
 * not thread-safe! do not use multiple executors to read from the same AwsChunked object
 * @param chan the underlying [SdkByteReadChannel] which will have its data encoded in aws-chunked format
 * @param signer the signer to use to sign chunks and (optionally) chunk trailer
 * @param signingConfig the config to use for signing
 * @previousSignature the previous signature to use for signing. in most cases, this should be the seed signature
 */
internal expect class AwsChunked internal constructor(
    chan: SdkByteReadChannel,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
) : AbstractAwsChunked
