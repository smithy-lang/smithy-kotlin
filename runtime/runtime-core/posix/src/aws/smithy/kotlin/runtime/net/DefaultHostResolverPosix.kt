/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import kotlinx.cinterop.*
import platform.posix.*

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> = memScoped {
        val hints = alloc<addrinfo>().apply {
            ai_family = AF_UNSPEC // Allow both IPv4 and IPv6
            ai_socktype = SOCK_STREAM // TCP stream sockets
            ai_flags = AI_PASSIVE // For wildcard IP address
        }

        val result = allocPointerTo<addrinfo>()

        try {
            // Perform the DNS lookup
            val status = getaddrinfo(hostname, null, hints.ptr, result.ptr)
            check(status == 0) { "Failed to resolve host $hostname: ${gai_strerror(status)?.toKString()}" }

            return generateSequence(result.value) { it.pointed.ai_next }
                .map { it.pointed.ai_addr!!.pointed.toIpAddr() }
                .map { HostAddress(hostname, it) }
                .toList()
        } finally {
            freeaddrinfo(result.value)
        }
    }

    @OptIn(UnsafeNumber::class)
    private fun sockaddr.toIpAddr(): IpAddr {
        val (size, addrPtr, constructor) = when (sa_family.toInt()) {
            AF_INET -> Triple(
                4,
                reinterpret<sockaddr_in>().sin_addr.ptr,
                { bytes: ByteArray -> IpV4Addr(bytes) },
            )
            AF_INET6 -> Triple(
                16,
                reinterpret<sockaddr_in6>().sin6_addr.ptr,
                { bytes: ByteArray -> IpV6Addr(bytes) },
            )
            else -> throw IllegalArgumentException("Unsupported sockaddr family $sa_family")
        }

        val ipBytes = ByteArray(size)
        memcpy(ipBytes.refTo(0), addrPtr, size.toULong())
        return constructor(ipBytes)
    }

    actual override fun reportFailure(addr: HostAddress) {
        // No-op, same as JVM implementation
    }

    actual override fun purgeCache(addr: HostAddress?) {
        // No-op, same as JVM implementation
    }
}
