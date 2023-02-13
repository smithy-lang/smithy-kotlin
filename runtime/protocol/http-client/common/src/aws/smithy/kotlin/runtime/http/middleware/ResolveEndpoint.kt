/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.client.endpoints.EndpointProvider
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.tracing.debug
import kotlin.coroutines.coroutineContext

/**
 * Http middleware for resolving the service endpoint.
 *
 * This is a static version of the otherwise-generated endpoint middleware and is intended for use by internal clients
 * within the runtime.
 */
@InternalApi
public class ResolveEndpoint<T>(
    private val provider: EndpointProvider<T>,
    private val params: T,
) : ModifyRequestMiddleware {

    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val endpoint = provider.resolveEndpoint(params)
        setResolvedEndpoint(req, endpoint)
        coroutineContext.debug<ResolveEndpoint<T>> { "resolved endpoint: $endpoint" }
        return req
    }
}

/**
 * Update an existing request with a resolved endpoint.
 *
 * Any values serialized to the HTTP path or query string are preserved (in the case of path, the existing serialized one
 * is appended to what was resolved).
 */
@InternalApi
public fun setResolvedEndpoint(req: SdkHttpRequest, endpoint: Endpoint) {
    val hostPrefix = req.context.getOrNull(HttpOperationContext.HostPrefix) ?: ""
    val hostname = "$hostPrefix${endpoint.uri.host}"
    val joinedPath = buildString {
        append(endpoint.uri.path.removeSuffix("/"))
        if (req.subject.url.path.isNotBlank()) {
            append("/")
            append(req.subject.url.path.removePrefix("/"))
        }
    }

    req.subject.url.scheme = endpoint.uri.scheme
    req.subject.url.userInfo = endpoint.uri.userInfo
    req.subject.url.host = Host.parse(hostname)
    req.subject.url.port = endpoint.uri.port
    req.subject.url.path = joinedPath
    req.subject.url.parameters.appendAll(endpoint.uri.parameters)
    req.subject.url.fragment = endpoint.uri.fragment

    req.subject.headers["Host"] = hostname
    endpoint.headers?.let { req.subject.headers.appendAll(it) }
}
