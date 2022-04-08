/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.endpoints

/**
 * Resolves endpoints for a given service and region
 */
fun interface AwsEndpointResolver {
    /**
     * Resolve the [AwsEndpoint] for the given service and region
     * @param service the service id associated with the desired endpoint
     * @param region the region associated with the desired endpoint
     * @return an [AwsEndpoint] that can be used to connect to the service
     */
    suspend fun resolve(service: String, region: String): AwsEndpoint
}
