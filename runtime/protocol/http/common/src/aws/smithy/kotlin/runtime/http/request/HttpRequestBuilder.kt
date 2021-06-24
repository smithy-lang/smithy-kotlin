/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.request

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Used to construct an HTTP request
 */
class HttpRequestBuilder {
    /**
     * The HTTP method (verb) to use when making the request
     */
    var method: HttpMethod = HttpMethod.GET

    /**
     * Endpoint to make request to
     */
    val url: UrlBuilder = UrlBuilder()

    /**
     * HTTP headers
     */
    val headers: HeadersBuilder = HeadersBuilder()

    /**
     * Outgoing payload. Initially empty
     */
    var body: HttpBody = HttpBody.Empty

    fun build(): HttpRequest = HttpRequest(method, url.build(), if (headers.isEmpty()) Headers.Empty else headers.build(), body)

    override fun toString(): String = buildString {
        append("HttpRequestBuilder(method=$method, url=$url, headers=$headers, body=$body)")
    }
}

// convenience extensions

/**
 * Modify the URL inside the block
 */
fun HttpRequestBuilder.url(block: UrlBuilder.() -> Unit) = url.apply(block)

/**
 * Modify the headers inside the given block
 */
fun HttpRequestBuilder.headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

/**
 * Add a single header. This will append to any existing headers with the same name.
 */
fun HttpRequestBuilder.header(name: String, value: String) = headers.append(name, value)

/**
 * Dump a debug description of the request
 *
 * @param dumpBody Flag controlling whether to also dump the body out. If true the body will be consumed and
 * replaced.
 */
@InternalApi
suspend fun dumpRequest(request: HttpRequestBuilder, dumpBody: Boolean): String {
    val buffer = SdkBuffer(256)

    // TODO - we have no way to know the http version at this level
    buffer.write("${request.method} ${request.url.encodedPath} HTTP/\r\n")
    buffer.write("Host: ${request.url.host}\r\n")

    val contentLength = request.headers["Content-Length"]?.toLongOrNull() ?: (request.body.contentLength ?: 0)
    if (contentLength > 0) {
        buffer.write("Content-Length: $contentLength\r\n")
    }

    val skip = setOf("Host", "Content-Length")
    request.headers.entries()
        .filterNot { it.key in skip }
        .forEach {
            buffer.write(it.value.joinToString(separator = ";", prefix = "${it.key}: ", postfix = "\r\n"))
        }

    buffer.write("\r\n")

    if (dumpBody) {
        when (val body = request.body) {
            is HttpBody.Bytes -> buffer.writeFully(body.bytes())
            is HttpBody.Streaming -> {
                // FIXME - would be better to rewind the stream if possible
                // consume the stream and replace the body
                val content = body.readAll()
                if (content != null) {
                    buffer.writeFully(content)
                    request.body = ByteArrayContent(content)
                }
            }
        }
    }

    return buffer.decodeToString()
}
