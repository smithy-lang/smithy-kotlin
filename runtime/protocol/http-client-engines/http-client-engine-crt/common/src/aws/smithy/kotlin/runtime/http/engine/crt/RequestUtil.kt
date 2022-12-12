/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HeadersBuilder
import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.http.HttpStream
import aws.sdk.kotlin.crt.io.Protocol
import aws.sdk.kotlin.crt.io.Uri
import aws.sdk.kotlin.crt.io.UserInfo
import aws.smithy.kotlin.runtime.crt.ReadChannelBodyStream
import aws.smithy.kotlin.runtime.crt.SdkSourceBodyStream
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.buffer
import aws.smithy.kotlin.runtime.io.readToByteArray
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
    val bodyStream = if (isChunked) null else when (body) {
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

/**
 * @return whether this HttpRequest is a chunked request.
 * Specifically, this means return `true` if a request contains a `Transfer-Encoding` header with the value `chunked`,
 * and the body is either [HttpBody.SourceContent] or [HttpBody.ChannelContent].
 */
internal val HttpRequest.isChunked: Boolean get() = (this.body is HttpBody.SourceContent || this.body is HttpBody.ChannelContent) &&
    headers.contains("Transfer-Encoding", "chunked")

/**
 * Send a chunked body using the CRT writeChunk bindings.
 * @param body an HTTP body that has a chunked content encoding. Must be [HttpBody.SourceContent] or [HttpBody.ChannelContent]
 */
internal suspend fun HttpStream.sendChunkedBody(body: HttpBody) {
    when (body) {
        is HttpBody.SourceContent -> {
            val source = body.readFrom()
            val bufferedSource = source.buffer()

            while (!bufferedSource.exhausted()) {
                bufferedSource.request(CHUNK_BUFFER_SIZE)
                writeChunk(bufferedSource.buffer.readByteArray(), isFinalChunk = bufferedSource.exhausted())
            }
        }
        is HttpBody.ChannelContent -> {
            val chan = body.readFrom()
            var buffer = SdkBuffer()
            val nextBuffer = SdkBuffer()
            var sentFirstChunk = false

            while (!chan.isClosedForRead) {
                val bytesRead = chan.read(buffer, CHUNK_BUFFER_SIZE)
                if (!sentFirstChunk && bytesRead == -1L) { throw RuntimeException("CRT does not support empty chunked bodies.") }

                val isFinalChunk = chan.read(nextBuffer, CHUNK_BUFFER_SIZE) == -1L

                writeChunk(buffer.readToByteArray(), isFinalChunk)
                if (isFinalChunk) break else buffer = nextBuffer
                sentFirstChunk = true
            }
        }
        else -> error("sendChunkedBody should not be called for non-chunked body types")
    }
}
