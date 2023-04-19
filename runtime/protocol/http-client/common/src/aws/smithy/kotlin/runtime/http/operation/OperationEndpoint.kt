/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Type agnostic version of [aws.smithy.kotlin.runtime.client.endpoints.EndpointProvider]. Typically service client
 * specific versions are code generated and then adapted to this generic version for actually executing a request.
 */
@InternalApi
public fun interface EndpointResolver {
    /**
     * Resolve the endpoint to send the request to
     * @param request The input context for the resolver function
     * @return an [Endpoint] that can be used to connect to the service
     */
    public suspend fun resolve(request: ResolveEndpointRequest): Endpoint
}

/**
 * Context for [EndpointResolver] implementations used to drive endpoint resolution
 * @param context the operation [ExecutionContext]
 * @param httpRequest the [HttpRequest] being built and executed
 * @param identity the resolved [Identity] for this request
 */
@InternalApi
public data class ResolveEndpointRequest(
    public val context: ExecutionContext,
    public val httpRequest: HttpRequest,
    public val identity: Identity,
)

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
