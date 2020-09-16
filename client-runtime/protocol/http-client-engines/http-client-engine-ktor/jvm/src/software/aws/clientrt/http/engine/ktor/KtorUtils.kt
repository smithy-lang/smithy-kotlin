/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine.ktor

import io.ktor.client.request.HttpRequestBuilder as KtorHttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import java.nio.ByteBuffer
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse as SdkHttpResponse
import software.aws.clientrt.io.Source

// convert everything **except** the body from an Sdk HttpRequestBuilder to equivalent Ktor abstraction
internal fun HttpRequestBuilder.toKtorRequestBuilder(): KtorHttpRequestBuilder {
    val builder = KtorHttpRequestBuilder()
    builder.method = HttpMethod.parse(this.method.name)
    val sdkUrl = this.url
    val sdkHeaders = this.headers
    builder.url {
        protocol = URLProtocol(sdkUrl.scheme.name.toLowerCase(), sdkUrl.scheme.defaultPort)
        host = sdkUrl.host
        port = sdkUrl.port ?: DEFAULT_PORT
        encodedPath = sdkUrl.path.encodeURLPath()
        if (!sdkUrl.parameters.isEmpty()) {
            val sdkParams = sdkUrl.parameters.build()
            sdkParams.forEach { name, values ->
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

// wrapper around ktor headers that implements expected SDK interface for Headers
internal class KtorHeaders(private val headers: Headers) : software.aws.clientrt.http.Headers {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String>? = headers.getAll(name)
    override fun names(): Set<String> = headers.names()
    override fun entries(): Set<Map.Entry<String, List<String>>> = headers.entries()
    override fun contains(name: String): Boolean = headers.contains(name)
    override fun isEmpty(): Boolean = headers.isEmpty()
}

// wrapper around ByteReadChannel that implements the [Source] interface
internal class KtorContentStream(private val channel: ByteReadChannel, private val onClose: (() -> Unit)? = null) : Source {
    override val availableForRead: Int
        get() = channel.availableForRead

    override val isClosedForRead: Boolean
        get() = channel.isClosedForRead

    override val isClosedForWrite: Boolean
        get() = channel.isClosedForWrite

    override suspend fun readAll(): ByteArray {
        val packet = channel.readRemaining()
        notifyIfExhausted()
        return packet.readBytes()
    }

    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) {
        channel.readFully(sink, offset, length)
        notifyIfExhausted()
    }

    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int {
        val read = channel.readAvailable(sink, offset, length)
        notifyIfExhausted()
        return read
    }

    override suspend fun readAvailable(sink: ByteBuffer): Int {
        val read = channel.readAvailable(sink)
        notifyIfExhausted()
        return read
    }

    override fun cancel(cause: Throwable?): Boolean {
        try {
            return channel.cancel(cause)
        } finally {
            onClose?.invoke()
        }
    }

    private fun notifyIfExhausted() {
        if (channel.isClosedForRead) {
            onClose?.invoke()
        }
    }
}

// wrapper around a ByteReadChannel that implements the content as an SDK (streaming) HttpBody
internal class KtorHttpBody(channel: ByteReadChannel, onClose: (() -> Unit)? = null) : HttpBody.Streaming() {
    private val source = KtorContentStream(channel, onClose)
    override fun readFrom(): Source = source
}

// convert ktor Http response to an (SDK) Http response
fun HttpResponse.toSdkHttpResponse(originalRequest: HttpRequest): SdkHttpResponse {
    val response = SdkHttpResponse(
        HttpStatusCode.fromValue(this.status.value),
        KtorHeaders(this.headers),
        KtorHttpBody(this.content),
        originalRequest
    )
    return response
}
