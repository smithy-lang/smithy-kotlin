/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client.endpoints

/**
 * Resolves endpoints for a given service client.
 */
public fun interface EndpointProvider<T> {
    /**
     * Resolve the endpoint to make requests to
     * @param params The input context for the resolver function
     * @return an [Endpoint] that can be used to connect to the service
     */
    public suspend fun resolveEndpoint(params: T): Endpoint
}
