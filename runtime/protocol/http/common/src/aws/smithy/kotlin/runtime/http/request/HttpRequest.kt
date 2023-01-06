/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.request

import aws.smithy.kotlin.runtime.http.*

/**
 * Immutable representation of an HTTP request
 */
public data class HttpRequest(
    public val method: HttpMethod,
    public val url: Url,
    public val headers: Headers,
    public val body: HttpBody,
    public val trailingHeaders: DeferredHeaders = DeferredHeaders.Empty,
) {
    public companion object {
        public operator fun invoke(block: HttpRequestBuilder.() -> Unit): HttpRequest =
            HttpRequestBuilder().apply(block).build()
    }
}

/**
 * Convert an HttpRequest back to an [HttpRequestBuilder]
 */
public fun HttpRequest.toBuilder(): HttpRequestBuilder {
    val req = this
    return HttpRequestBuilder().apply {
        method = req.method
        headers.appendAll(req.headers)
        url {
            scheme = req.url.scheme
            host = req.url.host
            port = req.url.port
            path = req.url.path
            parameters.appendAll(req.url.parameters)
            fragment = req.url.fragment
            userInfo = req.url.userInfo
            forceQuery = req.url.forceQuery
        }
        body = req.body
        req.trailingHeaders?.let { trailingHeaders.appendAll(it) }
    }
}
