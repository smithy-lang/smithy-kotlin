/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A resolved (hostname, address) pair
 */
public data class HostAddress(
    /**
     * The name that [address] was resolved from
     */
    val hostname: String,

    /**
     * The resolved internet address
     */
    val address: IpAddr,
)

/**
 * Component capable of resolving host names to one or more internet addresses
 */
@InternalApi
public interface HostResolver {
    public companion object {
        /**
         * The default DNS host resolver (usually the default for the platform, e.g. InetAddress for JVM)
         */
        public val Default: HostResolver = DefaultHostResolver
    }

    /**
     * Resolves the address(es) for a particular host and returns the list
     * of addresses
     */
    public suspend fun resolve(hostname: String): List<HostAddress>

    /**
     * Reports a failure on an address so that the cache can de-prioritize returning the address until it recovers
     */
    public fun reportFailure(addr: HostAddress): Unit

    /**
     * Purge the cache for all addresses or a specific address when [addr] is given
     */
    public fun purgeCache(addr: HostAddress? = null): Unit
}

internal expect object DefaultHostResolver : HostResolver
