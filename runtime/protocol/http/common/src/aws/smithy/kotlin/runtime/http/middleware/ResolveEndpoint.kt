/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 *  Http middleware for resolving the service endpoint.
 */
@InternalApi
class ResolveEndpoint<I, O>(
    private val resolver: EndpointResolver
) : MutateMiddleware<O>, AutoInstall<I, O> {

    override fun install(op: SdkHttpOperation<I, O>) {
        op.execution.mutate.register(this)
    }

    override suspend fun <H : Handler<SdkHttpRequest, O>> handle(request: SdkHttpRequest, next: H): O {
        val endpoint = resolver.resolve()
        setRequestEndpoint(request, endpoint)
        val logger = request.context.getLogger("ResolveEndpoint")
        logger.debug { "resolved endpoint: $endpoint" }
        return next.call(request)
    }
}

/**
 * Populate the request URL parameters from a resolved endpoint
 */
@InternalApi
fun setRequestEndpoint(req: SdkHttpRequest, endpoint: Endpoint) {
    val hostPrefix = req.context.getOrNull(HttpOperationContext.HostPrefix)
    val hostname = if (hostPrefix != null) "${hostPrefix}${endpoint.uri.host}" else endpoint.uri.host
    req.subject.url.scheme = endpoint.uri.scheme
    req.subject.url.host = hostname
    req.subject.url.port = endpoint.uri.port
    req.subject.headers["Host"] = hostname
    if (endpoint.uri.path.isNotBlank()) {
        val pathPrefix = endpoint.uri.path.removeSuffix("/")
        val original = req.subject.url.path.removePrefix("/")
        req.subject.url.path = "$pathPrefix/$original"
    }
}
