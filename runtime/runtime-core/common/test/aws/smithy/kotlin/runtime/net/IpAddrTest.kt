/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IpAddrTest {
    @Test
    fun testIpv4Ctor() {
        val fromOctets = IpAddr.Ipv4(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val fromSegments = IpAddr.Ipv4(0x11u, 0x22u, 0x33u, 0x44u)

        assertEquals(fromOctets, fromSegments)
    }

    @Test
    fun testLoopBack() {
        val ipv4 = IpAddr.Ipv4(127u, 0u, 0u, 1u)
        assertTrue(ipv4.isLoopBack)
        assertEquals(IpAddr.Ipv4.LOCALHOST, ipv4)
        assertTrue(IpAddr.Ipv4(127u, 255u, 255u, 254u).isLoopBack)

        val ipv6 = IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 1u)
        assertTrue(ipv6.isLoopBack)
        assertEquals(IpAddr.Ipv6.LOCALHOST, ipv6)
    }

    @Test
    fun testUnspecified() {
        val ipv4 = IpAddr.Ipv4(0u, 0u, 0u, 0u)
        assertTrue(ipv4.isUnspecified)
        assertEquals(IpAddr.Ipv4.UNSPECIFIED, ipv4)

        val ipv6 = IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)
        assertTrue(ipv6.isUnspecified)
        assertEquals(IpAddr.Ipv6.UNSPECIFIED, ipv6)
    }

    @Test
    fun testIpv6Ctor() {
        val fromSegments = IpAddr.Ipv6(
            0x0011u,
            0x2233u,
            0x4455u,
            0x6677u,
            0x8899u,
            0xaabbu,
            0xccddu,
            0xeeffu,
        )

        val fromOctets = IpAddr.Ipv6(
            byteArrayOf(
                0x00.toByte(), 0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte(), 0x77.toByte(), 0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
            ),
        )

        assertEquals(fromOctets, fromSegments)
    }

    @Test
    fun testIpv4ToString() {
        assertEquals("127.0.0.1", IpAddr.Ipv4(127u, 0u, 0u, 1u).toString())
        assertEquals("192.168.0.1", IpAddr.Ipv4(192u, 168u, 0u, 1u).toString())
        assertEquals("255.255.255.255", IpAddr.Ipv4(255u, 255u, 255u, 255u).toString())
    }

    @Test
    fun testIpv6ToString() {
        // no zero segments
        assertEquals(
            "8:9:a:b:c:d:e:f",
            IpAddr.Ipv6(8u, 9u, 10u, 11u, 12u, 13u, 14u, 15u).toString(),
        )

        // longest possible
        assertEquals(
            "1111:2222:3333:4444:5555:6666:7777:8888",
            IpAddr.Ipv6(0x1111u, 0x2222u, 0x3333u, 0x4444u, 0x5555u, 0x6666u, 0x7777u, 0x8888u).toString(),
        )

        // reduce run of zeroes
        assertEquals(
            "ae::ffff:102:304",
            IpAddr.Ipv6(0xaeu, 0u, 0u, 0u, 0u, 0xffffu, 0x0102u, 0x0304u).toString(),
        )

        // don't reduce single zero segment
        assertEquals(
            "1:2:3:4:5:6:0:8",
            IpAddr.Ipv6(1u, 2u, 3u, 4u, 5u, 6u, 0u, 8u).toString(),
        )

        // any/unspecified
        assertEquals("::", IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u).toString())

        // loopback
        assertEquals("::1", IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 1u).toString())

        // two different zero runs, 2nd is longer
        assertEquals(
            "1:0:0:4::8",
            IpAddr.Ipv6(1u, 0u, 0u, 4u, 0u, 0u, 0u, 8u).toString(),
        )

        // two runs equal length
        assertEquals(
            "1::4:5:0:0:8",
            IpAddr.Ipv6(1u, 0u, 0u, 4u, 5u, 0u, 0u, 8u).toString(),
        )

        // ends in zeros
        assertEquals("1::", IpAddr.Ipv6(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u).toString())
    }
}
