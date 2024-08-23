/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> = withContext(Dispatchers.IO) {
        InetAddress.getAllByName(hostname).map { it.toHostAddress() }
    }
    actual override fun reportFailure(addr: HostAddress) { }
    actual override fun purgeCache(addr: HostAddress?) { }
}

/**
 * Convert an InetAddress to an [IpAddr]
 */
@InternalApi
public fun InetAddress.toHostAddress(): HostAddress {
    val ipAddr = when (this) {
        is Inet4Address -> IpV4Addr(address)
        is Inet6Address -> IpV6Addr(address)
        else -> error("unrecognized InetAddress $this")
    }

    return HostAddress(hostName, ipAddr)
}

/**
 * Convert a host address to an [InetAddress]
 */
@InternalApi
public fun HostAddress.toInetAddress(): InetAddress = InetAddress.getByAddress(hostname, address.octets)
