/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.endpoints.EndpointResolver
import aws.smithy.kotlin.runtime.http.endpoints.setResolvedEndpoint
import aws.smithy.kotlin.runtime.http.operation.ModifyRequestMiddleware
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.http.operation.getLogger
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Http middleware for resolving the service endpoint.
 *
 * This is a static version of the otherwise-generated endpoint middleware and is intended for use by internal clients
 * within the runtime.
 */
@InternalApi
public class ResolveEndpoint(
    private val resolver: EndpointResolver,
) : ModifyRequestMiddleware {

    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val endpoint = resolver.resolve()
        setResolvedEndpoint(req, endpoint)
        val logger = req.context.getLogger("ResolveEndpoint")
        logger.debug { "resolved endpoint: $endpoint" }
        return req
    }
}
