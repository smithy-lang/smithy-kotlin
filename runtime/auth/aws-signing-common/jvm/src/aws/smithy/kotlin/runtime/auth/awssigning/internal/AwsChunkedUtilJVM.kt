package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedByteReadChannel
import aws.smithy.kotlin.runtime.auth.awssigning.AwsChunkedSource
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.http.toSdkByteReadChannel

internal actual fun HttpRequestBuilder.setAwsChunkedBody(signer: AwsSigner, signingConfig: AwsSigningConfig, signature: ByteArray) {
    body = when (body) {
        is HttpBody.ChannelContent -> AwsChunkedByteReadChannel(checkNotNull(body.toSdkByteReadChannel()), signer, signingConfig, signature).toHttpBody(-1)
        is HttpBody.SourceContent -> AwsChunkedSource((body as HttpBody.SourceContent).readFrom(), signer, signingConfig, signature).toHttpBody(body.contentLength ?: -1)
        else -> throw ClientException("HttpBody type is not supported")
    }
}