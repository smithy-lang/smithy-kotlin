/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedByteReadChannel
import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedSource
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder

internal actual fun HttpRequestBuilder.setAwsChunkedBody(signer: AwsSigner, signingConfig: AwsSigningConfig, signature: ByteArray, trailingHeaders: LazyHeaders) {
    body = when (body) {
        is HttpBody.ChannelContent -> AwsChunkedByteReadChannel(
            checkNotNull(body.toSdkByteReadChannel()),
            signer,
            signingConfig,
            signature,
            trailingHeaders,
        ).toHttpBody(-1)

        is HttpBody.SourceContent -> AwsChunkedSource(
            (body as HttpBody.SourceContent).readFrom(),
            signer,
            signingConfig,
            signature,
            trailingHeaders,
        ).toHttpBody(-1)

        else -> throw ClientException("HttpBody type is not supported")
    }
}
