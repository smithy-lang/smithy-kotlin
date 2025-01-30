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
            ai_family = AF_UNSPEC     // Allow both IPv4 and IPv6
            ai_socktype = SOCK_STREAM // TCP stream sockets
            ai_flags = AI_PASSIVE     // For wildcard IP address
        }

        val result = allocPointerTo<addrinfo>()

        // Perform the DNS lookup
        val status = getaddrinfo(hostname, null, hints.ptr, result.ptr)
        if (status != 0) {
            throw RuntimeException("Failed to resolve host $hostname: ${gai_strerror(status)?.toKString()}")
        }

        val addresses = mutableListOf<HostAddress>()
        var current = result.value

        while (current != null) {
            val sockaddr = current.pointed.ai_addr!!.pointed

            @OptIn(UnsafeNumber::class)
            when (sockaddr.sa_family.toInt()) {
                AF_INET -> {
                    val addr = sockaddr.reinterpret<sockaddr_in>()
                    val ipBytes = ByteArray(4)
                    memcpy(ipBytes.refTo(0), addr.sin_addr.ptr, 4uL)

                    addresses.add(HostAddress(
                        hostname = hostname,
                        address = IpV4Addr(ipBytes)
                    ))
                }
                AF_INET6 -> {
                    val addr = sockaddr.reinterpret<sockaddr_in6>()
                    val ipBytes = ByteArray(16)
                    memcpy(ipBytes.refTo(0), addr.sin6_addr.ptr, 16.convert())
                    addresses.add(HostAddress(
                        hostname = hostname,
                        address = IpV6Addr(ipBytes)
                    ))
                }
            }
            current = current.pointed.ai_next
        }

        // Free the getaddrinfo results
        freeaddrinfo(result.value)

        addresses
    }

    actual override fun reportFailure(addr: HostAddress) {
        // No-op, same as JVM implementation
    }

    actual override fun purgeCache(addr: HostAddress?) {
        // No-op, same as JVM implementation
    }
}