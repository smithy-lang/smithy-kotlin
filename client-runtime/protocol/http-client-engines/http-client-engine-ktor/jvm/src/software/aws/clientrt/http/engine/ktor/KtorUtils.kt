/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.engine.ktor

import io.ktor.client.request.HttpRequestBuilder as KtorHttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse as SdkHttpResponse
import software.aws.clientrt.io.Source

// convert Sdk HttpRequestBuilder to equivalent Ktor abstraction
fun HttpRequestBuilder.toKtorRequestBuilder(): KtorHttpRequestBuilder {
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

    // strip content type which ktor doesn't allow set like this
    val contentHeaders = sdkHeaders.remove("Content-Type")
    val contentType: ContentType? = contentHeaders?.first()?.let { ContentType.parse(it) }
    sdkHeaders.entries().forEach { (name, values) ->
        builder.headers.appendAll(name, values)
    }

    // wrap the request body
    when (this.body) {
        is HttpBody.Empty -> builder.body = EmptyContent
        is HttpBody.Bytes -> builder.body = ByteArrayContent((this.body as HttpBody.Bytes).bytes(), contentType)
        // FIXME - need to implement streaming still
        else -> throw NotImplementedError("streaming body not implemented yet")
    }

    return builder
}

// wrapper around ktor headers that implements expected SDK interface for Headers
class KtorHeaders(private val headers: Headers) : software.aws.clientrt.http.Headers {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String>? = headers.getAll(name)
    override fun names(): Set<String> = headers.names()
    override fun entries(): Set<Map.Entry<String, List<String>>> = headers.entries()
    override fun contains(name: String): Boolean = headers.contains(name)
    override fun isEmpty(): Boolean = headers.isEmpty()
}

// wrapper around ByteReadChannel that implements the [Source] interface
class KtorContentStream(val channel: ByteReadChannel) : Source {
    override val availableForRead: Int = channel.availableForRead
    override val isClosedForRead: Boolean = channel.isClosedForRead
    override val isClosedForWrite: Boolean = channel.isClosedForWrite

    override suspend fun readAll(): ByteArray {
        val packet = channel.readRemaining()
        return packet.readBytes()
    }

    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) =
            channel.readFully(sink, offset, length)

    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int =
            channel.readAvailable(sink, offset, length)

    override fun cancel(cause: Throwable?): Boolean = channel.cancel(cause)
}

// wrapper around a ByteReadChannel that implements the content as an SDK (streaming) HttpBody
class KtorHttpBody(val channel: ByteReadChannel) : HttpBody.Streaming() {
    private val source = KtorContentStream(channel)
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
