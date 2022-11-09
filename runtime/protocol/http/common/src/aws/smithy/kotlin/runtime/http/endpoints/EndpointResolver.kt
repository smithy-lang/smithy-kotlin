/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.endpoints

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Resolves endpoints for a given service client.
 *
 * This is a static version of the otherwise-generated endpoint resolver interface and is intended for use by internal
 * clients within the runtime.
 */
@InternalApi
public fun interface EndpointResolver {
    /**
     * Resolve the endpoint to make requests to
     * @return an [Endpoint] that can be used to connect to the service
     */
    public suspend fun resolve(): Endpoint
}
