/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

/**
 * Resolves endpoints for a given service client
 */
fun interface EndpointResolver {
    /**
     * Resolve the endpoint to make requests to
     * @return an [Endpoint] that can be used to connect to the service
     */
    public suspend fun resolve(): Endpoint
}
