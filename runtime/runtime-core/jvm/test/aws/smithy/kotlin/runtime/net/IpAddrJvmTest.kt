/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.net

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertEquals

class IpAddrJvmTest {
    @Test
    fun testFromInetAddr() {
        val tests = listOf(
            // ipv4
            InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) to IpV4Addr(127u, 0u, 0u, 1u),
            InetAddress.getByAddress(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())) to IpV4Addr(255u, 255u, 255u, 255u),
            InetAddress.getByAddress(byteArrayOf(0x11, 0x22, 0x33, 0x44)) to IpV4Addr(0x11u, 0x22u, 0x33u, 0x44u),

            // ipv6
            InetAddress.getByAddress(
                byteArrayOf(
                    0x00.toByte(), 0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte(), 0x77.toByte(), 0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
                ),
            ) to IpV6Addr(0x0011u, 0x2233u, 0x4455u, 0x6677u, 0x8899u, 0xaabbu, 0xccddu, 0xeeffu),
        )

        tests.forEach { test ->
            val actual = test.first.toHostAddress().address
            assertEquals(test.second, actual, "expected ${test.second}; got $actual; input=${test.first}")
        }
    }
}
