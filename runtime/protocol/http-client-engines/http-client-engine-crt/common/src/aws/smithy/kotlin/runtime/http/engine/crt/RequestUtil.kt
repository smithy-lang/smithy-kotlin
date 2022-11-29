/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HeadersBuilder
import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.io.Protocol
import aws.sdk.kotlin.crt.io.Uri
import aws.sdk.kotlin.crt.io.UserInfo
import aws.smithy.kotlin.runtime.crt.ReadChannelBodyStream
import aws.smithy.kotlin.runtime.crt.SdkSourceBodyStream
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

private const val CONTENT_LENGTH_HEADER: String = "Content-Length"

internal val HttpRequest.uri: Uri
    get() {
        val sdkUrl = this.url
        return Uri.build {
            scheme = Protocol.createOrDefault(sdkUrl.scheme.protocolName)
            host = sdkUrl.host.toString()
            port = sdkUrl.port
            userInfo = sdkUrl.userInfo?.let { UserInfo(it.username, it.password) }
            // the rest is part of each individual request, manager only needs the host info
        }
    }

internal fun HttpRequest.toCrtRequest(callContext: CoroutineContext): aws.sdk.kotlin.crt.http.HttpRequest {
    val body = this.body
    check(!body.isDuplex) { "CrtHttpEngine does not yet support full duplex streams" }
    val bodyStream = when (body) {
        is HttpBody.Empty -> null
        is HttpBody.Bytes -> HttpRequestBodyStream.fromByteArray(body.bytes())
        is HttpBody.ChannelContent -> ReadChannelBodyStream(body.readFrom(), callContext)
        is HttpBody.SourceContent -> {
            val source = body.readFrom()
            callContext.job.invokeOnCompletion {
                source.close()
            }
            SdkSourceBodyStream(source)
        }
    }

    val crtHeaders = HeadersBuilder()
    with(crtHeaders) {
        headers.forEach { key, values -> appendAll(key, values) }
    }

    val bodyLen = body.contentLength
    val contentLength = when {
        bodyLen != null -> if (bodyLen > 0) bodyLen.toString() else null
        else -> headers[CONTENT_LENGTH_HEADER]
    }
    contentLength?.let { crtHeaders.append(CONTENT_LENGTH_HEADER, it) }

    return aws.sdk.kotlin.crt.http.HttpRequest(method.name, url.encodedPath, crtHeaders.build(), bodyStream)
}
