/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

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

    // TODO - these feel like separate concerns (caching could/should probably wrap HostResolver rather than be integrated)
    // e.g. DnsCache(val resolver: HostResolver): HostResolver { ... }
    // /**
    //  * Report a failure to connect on an address so that the cache can de-prioritize
    //  * returning the address until it recovers
    //  */
    // public fun reportFailure(addr: IpAddr)
    //
    // /**
    //  * Purge the cache for all addresses or a specific address when [addr] is given
    //  */
    // public fun purgeCache(addr: IpAddr? = null)
}

internal expect object DefaultHostResolver : HostResolver
