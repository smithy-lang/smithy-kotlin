/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.ProtocolResponse
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.LOG_REDACTED_HEADERS_KEY
import aws.smithy.kotlin.runtime.http.SENSITIVE_DATA_REDACTED
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Immutable container for an HTTP response
 */
public sealed interface HttpResponse : ProtocolResponse {
    /**
     * The response status code
     */
    public val status: HttpStatusCode

    /**
     * The response headers
     */
    public val headers: Headers

    /**
     * The response body
     */
    public val body: HttpBody
}

/**
 * Use the default HTTP response implementation
 */
public fun HttpResponse(
    status: HttpStatusCode,
    headers: Headers = Headers.Empty,
    body: HttpBody = HttpBody.Empty,
): HttpResponse = DefaultHttpResponse(status, headers, body)

private data class DefaultHttpResponse(
    override val status: HttpStatusCode,
    override val headers: Headers,
    override val body: HttpBody,
) : HttpResponse {
    override val summary: String = "HTTP ${status.value} ${status.description}"
}

/**
 * Replace the response body
 */
public fun HttpResponse.copy(
    status: HttpStatusCode = this.status,
    headers: Headers = this.headers,
    body: HttpBody = this.body,
): HttpResponse = HttpResponse(status, headers, body)

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
@Deprecated(
    "Use the overload that accepts context parameter",
    ReplaceWith("dumpResponse(response, context, dumpBody)"),
)
@PlannedRemoval(major = 1, minor = 8)
@InternalApi
public suspend fun dumpResponse(
    response: HttpResponse,
    dumpBody: Boolean,
): Pair<HttpResponse, String> = dumpResponse(response, ExecutionContext(), dumpBody)

/**
 * Dump a debug description of the response. Either the original response or a copy will be returned to the caller
 * depending on if the body is consumed.
 *
 * @param context The execution context containing configuration such as redacted headers.
 * @param dumpBody Flag controlling whether to also dump the body out. If true the body will be consumed and
 * replaced.
 */
@InternalApi
public suspend fun dumpResponse(
    response: HttpResponse,
    context: ExecutionContext,
    dumpBody: Boolean,
): Pair<HttpResponse, String> {
    val redactedHeaders = context.getOrNull(LOG_REDACTED_HEADERS_KEY) ?: emptySet()
    val buffer = SdkBuffer()
    buffer.writeUtf8("HTTP ${response.status}\r\n")
    response.headers.forEach { key, values ->
        val headerValue = if (redactedHeaders.any { it.equals(key, ignoreCase = true) }) {
            SENSITIVE_DATA_REDACTED
        } else {
            values.joinToString(separator = ";")
        }
        buffer.writeUtf8("$key: $headerValue\r\n")
    }
    buffer.writeUtf8("\r\n")

    var respCopy = response
    if (dumpBody) {
        when (val body = response.body) {
            is HttpBody.Bytes -> buffer.write(body.bytes())
            is HttpBody.ChannelContent, is HttpBody.SourceContent -> {
                // consume the stream and replace the body. There isn't much rewinding we can do here, most engines
                // use a stream that reads right off the wire.
                val content = body.readAll()
                if (content != null) {
                    buffer.write(content)
                    val newBody = ByteArrayContent(content)
                    respCopy = response.copy(body = newBody)
                }
            }
            is HttpBody.Empty -> { } // nothing to dump
        }
    }

    return respCopy to buffer.readUtf8()
}

/**
 * Convert an HttpResponse to an [HttpResponseBuilder]
 */
public fun HttpResponse.toBuilder(): HttpResponseBuilder = when (this) {
    is HttpResponseBuilderView -> {
        check(allowToBuilder) { "This is an immutable HttpResponse that should not be converted to a builder" }
        builder
    }
    else -> {
        val resp = this
        HttpResponseBuilder().apply {
            status = resp.status
            headers.appendAll(resp.headers)
            body = resp.body
        }
    }
}
