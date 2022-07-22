/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.ProtocolResponse
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Immutable container for an HTTP response
 *
 * @property [status] response status code
 * @property [headers] response headers
 * @property [body] response body content
 */
public data class HttpResponse(
    public val status: HttpStatusCode,
    public val headers: Headers,
    public val body: HttpBody,
) : ProtocolResponse

/**
 * Get an HTTP header value by name. Returns the first header if multiple headers are set
 */
public fun ProtocolResponse.header(name: String): String? {
    val httpResp = this as? HttpResponse
    return httpResp?.headers?.get(name)
}

/**
 * Get all HTTP header values associated with the given name.
 */
public fun ProtocolResponse.getAllHeaders(name: String): List<String>? {
    val httpResp = this as? HttpResponse
    return httpResp?.headers?.getAll(name)
}

/**
 * Get the HTTP status code of the response
 */
public fun ProtocolResponse.statusCode(): HttpStatusCode? {
    val httpResp = this as? HttpResponse
    return httpResp?.status
}

/**
 * Dump a debug description of the response. Either the original response or a copy will be returned to the caller
 * depending on if the body is consumed.
 *
 * @param dumpBody Flag controlling whether to also dump the body out. If true the body will be consumed and
 * replaced.
 */
@InternalApi
public suspend fun dumpResponse(response: HttpResponse, dumpBody: Boolean): Pair<HttpResponse, String> {
    val buffer = SdkByteBuffer(256u)
    buffer.write("HTTP ${response.status}\r\n")
    response.headers.forEach { key, values ->
        buffer.write(values.joinToString(separator = ";", prefix = "$key: ", postfix = "\r\n"))
    }
    buffer.write("\r\n")

    var respCopy = response
    if (dumpBody) {
        when (val body = response.body) {
            is HttpBody.Bytes -> buffer.writeFully(body.bytes())
            is HttpBody.Streaming -> {
                // consume the stream and replace the body. There isn't much rewinding we can do here, most engines
                // use a stream that reads right off the wire.
                val content = body.readAll()
                if (content != null) {
                    buffer.writeFully(content)
                    val newBody = ByteArrayContent(content)
                    respCopy = response.copy(body = newBody)
                }
            }
            is HttpBody.Empty -> { } // nothing to dump
        }
    }

    return respCopy to buffer.decodeToString()
}
