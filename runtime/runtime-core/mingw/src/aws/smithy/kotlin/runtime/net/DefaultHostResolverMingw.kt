package aws.smithy.kotlin.runtime.net

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_STREAM
import platform.posix.WSADATA
import platform.posix.memcpy
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.windows.AI_PASSIVE
import platform.windows.WSACleanup
import platform.windows.WSAStartup
import platform.windows.addrinfo
import platform.windows.freeaddrinfo
import platform.windows.gai_strerror
import platform.windows.getaddrinfo
import platform.windows.sockaddr_in6

internal actual object DefaultHostResolver : HostResolver {
    actual override suspend fun resolve(hostname: String): List<HostAddress> = memScoped {
        // Version format specified in https://learn.microsoft.com/en-us/windows/win32/api/winsock/nf-winsock-wsastartup
        val wsaMajorVersion = 1u
        val wsaMinorVersion = 1u
        val wsaVersion = (wsaMajorVersion shl 8 or wsaMinorVersion).toUShort()

        val wsaInfo = alloc<WSADATA>()
        val wsaResult = WSAStartup(wsaVersion, wsaInfo.ptr)
        check(wsaResult == 0) { "Failed to initialize Windows Sockets (error code $wsaResult)" }

        try {

            val hints = alloc<addrinfo>().apply {
                ai_family = AF_UNSPEC // Allow both IPv4 and IPv6
                ai_socktype = SOCK_STREAM // TCP stream sockets
                ai_flags = AI_PASSIVE // For wildcard IP address
            }

            val result = allocPointerTo<addrinfo>()

            try {
                // Perform the DNS lookup
                val status = getaddrinfo(hostname, null, hints.ptr, result.ptr)
                check(status == 0) { "Failed to resolve host $hostname: ${gai_strerror?.invoke(status)?.toKString()}" }

                return generateSequence(result.value) { it.pointed.ai_next }
                    .map { it.pointed.ai_addr!!.pointed.toIpAddr() }
                    .map { HostAddress(hostname, it) }
                    .toList()
            } finally {
                freeaddrinfo(result.value)
            }
        } finally {
            WSACleanup()
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
