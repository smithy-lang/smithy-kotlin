/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.nio.ByteBuffer
import aws.smithy.kotlin.runtime.http.response.HttpResponse as SdkHttpResponse
import io.ktor.client.request.HttpRequestBuilder as KtorHttpRequestBuilder

// convert everything **except** the body from an Sdk HttpRequestBuilder to equivalent Ktor abstraction
// TODO: Remove following annotation after https://youtrack.jetbrains.com/issue/KTOR-3001 is resolved
@OptIn(InternalAPI::class)
internal fun HttpRequest.toKtorRequestBuilder(): KtorHttpRequestBuilder {
    val builder = KtorHttpRequestBuilder()
    builder.method = HttpMethod.parse(this.method.name)
    val sdkUrl = this.url
    val sdkHeaders = this.headers
    builder.url {
        val protocolName = sdkUrl.scheme.protocolName.replaceFirstChar(Char::lowercaseChar)
        protocol = URLProtocol(protocolName, sdkUrl.scheme.defaultPort)
        host = sdkUrl.host
        port = sdkUrl.port
        encodedPath = sdkUrl.path.encodeURLPath()
        if (!sdkUrl.parameters.isEmpty()) {
            sdkUrl.parameters.entries().forEach { (name, values) ->
                parameters.appendAll(name, values)
            }
        }
        sdkUrl.fragment?.let { fragment = it }
        trailingQuery = sdkUrl.forceQuery
        sdkUrl.userInfo?.let {
            user = it.username
            password = it.password
        }
    }

    sdkHeaders.entries().forEach { (name, values) ->
        builder.headers.appendAll(name, values)
    }

    return builder
}

internal fun HttpRequestBuilder.toKtorRequestBuilder(): KtorHttpRequestBuilder = build().toKtorRequestBuilder()

// wrapper around ktor headers that implements expected SDK interface for Headers
internal class KtorHeaders(private val headers: Headers) : aws.smithy.kotlin.runtime.http.Headers {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String>? = headers.getAll(name)
    override fun names(): Set<String> = headers.names()
    override fun entries(): Set<Map.Entry<String, List<String>>> = headers.entries()
    override fun contains(name: String): Boolean = headers.contains(name)
    override fun isEmpty(): Boolean = headers.isEmpty()
}

// wrapper around ByteReadChannel that implements the [Source] interface
internal class KtorContentStream(private val channel: ByteReadChannel) : SdkByteReadChannel {
    override val availableForRead: Int
        get() = channel.availableForRead

    override val isClosedForRead: Boolean
        get() = channel.isClosedForRead

    override val isClosedForWrite: Boolean
        get() = channel.isClosedForWrite

    override suspend fun readRemaining(limit: Int): ByteArray {
        val packet = channel.readRemaining(limit.toLong())
        return packet.readBytes()
    }

    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) {
        channel.readFully(sink, offset, length)
    }

    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int =
        channel.readAvailable(sink, offset, length)

    override suspend fun readAvailable(sink: ByteBuffer): Int = channel.readAvailable(sink)

    override suspend fun awaitContent() = channel.awaitContent()

    override fun cancel(cause: Throwable?): Boolean = channel.cancel(cause)
}

// wrapper around a ByteReadChannel that implements the content as an SDK (streaming) HttpBody
internal class KtorHttpBody(
    override val contentLength: Long? = null,
    channel: ByteReadChannel
) : HttpBody.Streaming() {
    private val source = KtorContentStream(channel)
    override fun readFrom(): SdkByteReadChannel = source
}

// convert ktor Http response to an (SDK) Http response
fun HttpResponse.toSdkHttpResponse(): SdkHttpResponse = SdkHttpResponse(
    HttpStatusCode.fromValue(status.value),
    KtorHeaders(headers),
    KtorHttpBody(contentLength(), channel = content)
)
