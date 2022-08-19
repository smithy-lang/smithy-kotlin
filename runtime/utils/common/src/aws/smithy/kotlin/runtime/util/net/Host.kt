/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.util.net

import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent

/**
 * A [Host] represents a parsed internet host. This may be an internet address (IPv4, IPv6) or a domain name.
 */
public sealed class Host {
    public companion object {
        public fun parse(host: String): Host = hostParseImpl(host)
    }

    public data class IPv4(
        public val address: String,
    ) : Host() {
        public override fun toString(): String = address
    }

    public data class IPv6(
        public val address: String,
        public val scopeId: String? = null,
    ) : Host() {
        public override fun toString(): String = if (scopeId == null) address else "$address%$scopeId"
    }

    public data class Domain(
        public val name: String,
    ) : Host() {
        public override fun toString(): String = name
    }
}

private fun hostParseImpl(host: String): Host =
    when {
        host.isIpv4() -> Host.IPv4(host)
        host.isIpv6() -> {
            val components = host.split('%')
            Host.IPv6(
                components[0],
                if (components.size > 1) components[1] else null,
            )
        }
        host.split('.').all(String::isValidHostname) -> Host.Domain(host)
        else -> throw IllegalArgumentException("$host is not a valid inet host")
    }

public fun Host.toUrlString(): String =
    when (this) {
        is Host.IPv4 -> address
        is Host.IPv6 -> if (scopeId == null) "[$address]" else "[$address%25${scopeId.urlEncodeComponent()}]"
        is Host.Domain -> name
    }
