/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.endpoints.discovery

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.ResolveEndpointRequest
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.util.ExpiringValue
import aws.smithy.kotlin.runtime.util.ReadThroughCache
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@InternalApi
public class DiscoveredEndpointResolver(
    private val delegate: EndpointResolver,
    private val getRegion: () -> String,
    cacheSweepPeriod: Duration = 10.minutes,
    clock: Clock = Clock.System,
    private val discoverHosts: suspend () -> List<ExpiringValue<Host>>,
) : EndpointResolver {
    private val cache = ReadThroughCache<DiscoveryParams, Host>(cacheSweepPeriod, clock) {
        requireNotNull(discoverHosts().firstOrNull()) { "No endpoints discovered!" }
    }

    override suspend fun resolve(request: ResolveEndpointRequest): Endpoint {
        val identity = request.identity
        require(identity is Credentials) { "Endpoint discovery requires AWS credentials" }

        val region = getRegion()
        val cacheKey = DiscoveryParams(region, identity.accessKeyId)
        val discoveredHost = cache.get(cacheKey)

        val originalEndpoint = delegate.resolve(request)
        return Endpoint(
            originalEndpoint.uri.copy(host = discoveredHost),
            originalEndpoint.headers,
            originalEndpoint.attributes,
        )
    }
}

private data class DiscoveryParams(private val region: String, private val identity: String)
