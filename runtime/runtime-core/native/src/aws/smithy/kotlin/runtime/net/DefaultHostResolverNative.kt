/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.sdk.kotlin.crt.io.CrtHostAddress
import aws.sdk.kotlin.crt.io.HostResolver as CrtHostResolver

public class HostResolverNative : HostResolver {
    private val delegate = CrtHostResolver()

    override suspend fun resolve(hostname: String): List<HostAddress> {
        val results = delegate.resolve(hostname)
        return results.map { it.toHostAddress() }
    }

    // No-op, matches JVM implementation
    override fun purgeCache(addr: HostAddress?) { }

    // No-op, matches JVM implementation
    override fun reportFailure(addr: HostAddress) { }

    private fun CrtHostAddress.toHostAddress() = HostAddress(host, IpAddr.parse(address))
}