/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import kotlin.test.*

class IpAddrTest {
    private fun ipv4(a: Int, b: Int, c: Int, d: Int): IpAddr.Ipv4 = IpAddr.Ipv4(a.toUByte(), b.toUByte(), c.toUByte(), d.toUByte())

    @Test
    fun testIpv4Ctor() {
        val fromOctets = IpAddr.Ipv4(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val fromSegments = IpAddr.Ipv4(0x11u, 0x22u, 0x33u, 0x44u)

        assertEquals(fromOctets, fromSegments)
    }

    @Test
    fun testIpv4Parse() {
        assertEquals(ipv4(192, 168, 0, 1), IpAddr.Ipv4.parse("192.168.0.1"))
        assertEquals(ipv4(127, 0, 0, 1), IpAddr.Ipv4.parse("127.0.0.1"))
        assertEquals(ipv4(255, 255, 255, 255), IpAddr.Ipv4.parse("255.255.255.255"))

        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv4.parse("256.0.0.1")
        }
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv4.parse("255.0.0")
        }
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv4.parse("255.0.0.1.2")
        }
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv4.parse("255.0..255")
        }
    }

    @Test
    fun testIpv4MappedIpv6() {
        val v4 = ipv4(192, 168, 0, 1)
        val v6 = IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0xffffu, 0xc0a8u, 0x1u)
        assertEquals(v6, v4.toMappedIpv6())
    }

    @Test
    fun testIpv6Parse() {
        assertEquals(IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), IpAddr.Ipv6.parse("0:0:0:0:0:0:0:0"))
        assertEquals(IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), IpAddr.Ipv6.parse("::"))
        assertEquals(IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 1u), IpAddr.Ipv6.parse("0:0:0:0:0:0:0:1"))
        assertEquals(IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0u, 1u), IpAddr.Ipv6.parse("::1"))

        assertEquals(
            IpAddr.Ipv6(0x2001u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u),
            IpAddr.Ipv6.parse("2001:db8::2:1"),
        )

        assertEquals(
            IpAddr.Ipv6(0x2001u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u),
            IpAddr.Ipv6.parse("2001:db8:0:0:0:0:2:1"),
        )

        // w/zone id
        assertEquals(
            IpAddr.Ipv6(0x2001u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u, "eth0"),
            IpAddr.Ipv6.parse("2001:db8::2:1%eth0"),
        )

        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv6.parse("1:2:3:4:5:6:7")
        }

        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv6.parse("1:2:3:4:5:6:7:8:9")
        }

        // triple colon
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv6.parse("1:2:::6:7:8")
        }

        // two double colon
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv6.parse("1:2::6::8")
        }

        // zero group of zeroes
        assertFailsWith<IllegalArgumentException> {
            IpAddr.Ipv6.parse("1:2:3::4:5:6:7:8")
        }
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

        // IPv4 mapped
        assertEquals("::ffff:192.0.7.128", IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0xffffu, 0xc000u, 0x780u).toString())

        // IPv4 compatible - we don't support the special syntax (only for mapped)
        assertEquals("::c000:780", IpAddr.Ipv6(0u, 0u, 0u, 0u, 0u, 0u, 0xc000u, 0x780u).toString())

        // w/zone id
        assertEquals("2001:db8::2:1%eth0", IpAddr.Ipv6(0x2001u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u, "eth0").toString())
    }

    @Test
    fun testIpv6Properties() {
        assertFalse(
            IpAddr.Ipv6(0x2001u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).isMulticast,
        )

        assertTrue(
            IpAddr.Ipv6(0xff01u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).isMulticast,
        )

        assertEquals(
            Ipv6MulticastScope.InterfaceLocal,
            IpAddr.Ipv6(0xff01u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.LinkLocal,
            IpAddr.Ipv6(0xff02u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.RealmLocal,
            IpAddr.Ipv6(0xff03u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.AdminLocal,
            IpAddr.Ipv6(0xff04u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.SiteLocal,
            IpAddr.Ipv6(0xff05u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.OrganizationLocal,
            IpAddr.Ipv6(0xff08u, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )

        assertEquals(
            Ipv6MulticastScope.Global,
            IpAddr.Ipv6(0xff0eu, 0xdb8u, 0u, 0u, 0u, 0u, 2u, 1u).multicastScope,
        )
    }
}
