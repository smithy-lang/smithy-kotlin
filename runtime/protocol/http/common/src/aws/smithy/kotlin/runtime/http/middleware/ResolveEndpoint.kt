/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.endpoints.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 *  Http middleware for resolving the service endpoint.
 */
@InternalApi
class ResolveEndpoint(
    private val resolver: EndpointResolver
) : ModifyRequestMiddleware {

    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val endpoint = resolver.resolve()
        setRequestEndpoint(req, endpoint)
        val logger = req.context.getLogger("ResolveEndpoint")
        logger.debug { "resolved endpoint: $endpoint" }
        return req
    }
}

/**
 * Populate the request URL parameters from a resolved endpoint
 */
@InternalApi
fun setRequestEndpoint(req: SdkHttpRequest, endpoint: Endpoint) {
    val hostPrefix = req.context.getOrNull(HttpOperationContext.HostPrefix)
    val hostname = if (hostPrefix != null && !endpoint.isHostnameImmutable) {
        "$hostPrefix${endpoint.uri.host}"
    } else {
        endpoint.uri.host
    }

    req.subject.url.scheme = endpoint.uri.scheme
    req.subject.url.host = hostname
    req.subject.url.port = endpoint.uri.port
    req.subject.headers["Host"] = hostname
    if (endpoint.uri.path.isNotBlank()) {
        val opPath = req.subject.url.path
        val endpointPath = endpoint.uri.path.removeSuffix("/")
        req.subject.url.path = if (opPath.isNotBlank()) {
            "$endpointPath/${opPath.removePrefix("/")}"
        } else {
            // keep endpoint path as is
            endpoint.uri.path
        }
    }

    req.subject.url.parameters.appendAll(endpoint.uri.parameters)
}
