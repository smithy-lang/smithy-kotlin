/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HostResolverTest {
    @Test
    fun testResolveLocalhost() = runTest {
        val addresses = HostResolver.Default.resolve("localhost")
        assertTrue(addresses.isNotEmpty())

        addresses.forEach { addr ->
            assertEquals("localhost", addr.hostname)
            when (val ip = addr.address) {
                is IpV4Addr -> {
                    assertEquals(4, ip.octets.size)
                    // localhost should resolve to 127.0.0.1
                    assertContentEquals(byteArrayOf(127, 0, 0, 1), ip.octets)
                }
                is IpV6Addr -> {
                    assertEquals(16, ip.octets.size)
                    // ::1 in IPv6
                    val expectedIpv6 = ByteArray(16) { 0 }
                    expectedIpv6[15] = 1
                    assertContentEquals(expectedIpv6, ip.octets)
                }
            }
        }
    }

    @Test
    fun testResolveIpv4Address() = runTest {
        val addresses = HostResolver.Default.resolve("127.0.0.1")
        assertTrue(addresses.isNotEmpty())

        addresses.forEach { addr ->
            assertTrue(addr.address is IpV4Addr)
            assertContentEquals(byteArrayOf(127, 0, 0, 1), addr.address.octets)
        }
    }

    @Test
    fun testResolveIpv6Address() = runTest {
        val addresses = HostResolver.Default.resolve("::1")
        assertTrue(addresses.isNotEmpty())

        addresses.forEach { addr ->
            assertTrue(addr.address is IpV6Addr)
            val expectedBytes = ByteArray(16) { 0 }
            expectedBytes[15] = 1
            assertContentEquals(expectedBytes, addr.address.octets)
        }
    }

    @Test
    fun testResolveExampleDomain() = runTest {
        val addresses = HostResolver.Default.resolve("example.com")
        assertNotNull(addresses)
        assertTrue(addresses.isNotEmpty())

        addresses.forEach { addr ->
            assertEquals("example.com", addr.hostname)
            when (val ip = addr.address) {
                is IpV4Addr -> assertEquals(4, ip.octets.size)
                is IpV6Addr -> assertEquals(16, ip.octets.size)
            }
        }
    }

    @Test
    fun testResolveInvalidDomain() = runTest {
        assertFails {
            HostResolver.Default.resolve("this-domain-definitely-does-not-exist-12345.local")
        }
    }

    @Test
    fun testNoopMethods() {
        // Test no-op methods don't throw
        val dummyAddr = HostAddress("test.com", IpV4Addr(ByteArray(4)))
        val resolver = HostResolver.Default
        resolver.reportFailure(dummyAddr)
        resolver.purgeCache(null)
        resolver.purgeCache(dummyAddr)
    }
}