/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.toSdkChannel
import aws.smithy.kotlin.runtime.util.text.encodeUrlPath
import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineDispatcher
import io.ktor.client.request.HttpRequestBuilder as KtorHttpRequestBuilder

// convert everything **except** the body from an Sdk HttpRequestBuilder to equivalent Ktor abstraction
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
        encodedPath = sdkUrl.path.encodeUrlPath()

        if (!sdkUrl.parameters.isEmpty()) {
            sdkUrl.parameters.entries().forEach { (name, values) ->
                // if parameters are already encoded don't double encode them
                val encoded = if (sdkUrl.encodeParameters) values.map { it.urlEncodeComponent() } else values
                encodedParameters.appendAll(name, encoded)
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

// wrapper around a ByteReadChannel that implements the content as an SDK (streaming) HttpBody
internal class KtorHttpBody(
    override val contentLength: Long? = null,
    channel: ByteReadChannel,
) : HttpBody.Streaming() {
    private val source = channel.toSdkChannel()
    override fun readFrom(): SdkByteReadChannel = source
}

/**
 * Copy all of (SDK) [source] to (Ktor) [dst]. This allows implementations to use whatever
 * buffering is most efficient for the platform.
 */
internal expect suspend fun forwardSource(dst: ByteChannel, source: SdkByteReadChannel)

/**
 * Get the appropriate dispatcher for doing IO bound work for the platform
 */
internal expect fun ioDispatcher(): CoroutineDispatcher
