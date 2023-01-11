/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.request

import aws.smithy.kotlin.runtime.http.*

/**
 * Immutable representation of an HTTP request
 */
public sealed interface HttpRequest {
    /**
     * The HTTP method (verb) to use when sending the request
     */
    public val method: HttpMethod

    /**
     * The endpoint to send the request to
     */
    public val url: Url

    /**
     * The headers to send with the request
     */
    public val headers: Headers

    /**
     * The request payload
     */
    public val body: HttpBody

    /**
     * The trailing headers
     */
    public val trailingHeaders: DeferredHeaders = DeferredHeaders.Empty,

    public companion object {
        public operator fun invoke(block: HttpRequestBuilder.() -> Unit): HttpRequest =
            HttpRequestBuilder().apply(block).build()
    }
}

/**
 * Create a new [HttpRequest]
 */
public fun HttpRequest(
    method: HttpMethod,
    url: Url,
    headers: Headers,
    body: HttpBody,
): HttpRequest = RealHttpRequest(method, url, headers, body)

private data class RealHttpRequest(
    override val method: HttpMethod,
    override val url: Url,
    override val headers: Headers,
    override val body: HttpBody,
) : HttpRequest

/**
 * Convert an HttpRequest back to an [HttpRequestBuilder]
 */
public fun HttpRequest.toBuilder(): HttpRequestBuilder = when (this) {
    is HttpRequestBuilderView -> {
        check(allowToBuilder) { "This is an immutable HttpRequest that should not be converted to a builder" }
        builder
    }
    is RealHttpRequest -> {
        val req = this
        HttpRequestBuilder().apply {
            method = req.method
            headers.appendAll(req.headers)
            url(req.url)
            body = req.body
            trailingHeaders.appendAll(req.trailingHeaders)
        }
    }
}
