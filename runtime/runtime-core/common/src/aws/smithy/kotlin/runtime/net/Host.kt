/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.text.urlEncodeComponent

/**
 * A [Host] represents a parsed internet host. This may be an internet address (IPv4, IPv6) or a domain name.
 */
public sealed class Host {
    public companion object {
        public fun parse(host: String): Host = hostParseImpl(host)
    }

    public data class IpAddress(
        public val address: IpAddr,
    ) : Host() {
        override fun toString(): String = address.toString()
    }

    public data class Domain(
        public val name: String,
    ) : Host() {
        public override fun toString(): String = name
    }
}

private fun hostParseImpl(host: String): Host {
    val ip = host.parseIpv4OrNull() ?: host.parseIpv6OrNull()
    return when {
        ip != null -> Host.IpAddress(ip)
        host.split('.').all(String::isValidHostname) -> Host.Domain(host)
        else -> throw IllegalArgumentException("$host is not a valid inet host")
    }
}

public fun Host.toUrlString(): String =
    when (this) {
        is Host.IpAddress -> when (address) {
            is IpV6Addr -> {
                if (address.zoneId == null) {
                    "[$address]"
                } else {
                    val withoutZoneId = address.copy(zoneId = null)
                    "[$withoutZoneId%25${address.zoneId.urlEncodeComponent()}]"
                }
            }
            else -> address.toString()
        }
        is Host.Domain -> name
    }
