/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.util.net

import kotlin.test.*

class TextTest {
    @Test
    fun testSplitHostPortFullyQualified() {
        val (host, port) = "localhost:1024".splitHostPort()
        assertEquals("localhost", host)
        assertEquals(1024, port)
    }

    @Test
    fun testSplitHostPortHostnameOnly() {
        val (host, port) = "localhost".splitHostPort()
        assertEquals("localhost", host)
        assertNull(port)
    }

    @Test
    fun testSplitHostPortFullyQualifiedIpv6() {
        val (host, port) = "[fe80::]:1024".splitHostPort()
        assertEquals("fe80::", host)
        assertEquals(1024, port)
    }

    @Test
    fun testSplitHostPortHostnameOnlyIpv6() {
        val (host, port) = "[fe80::]".splitHostPort()
        assertEquals("fe80::", host)
        assertNull(port)
    }

    @Test
    fun testInvalidLengthHostname() =
        assertFalse("asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf".isValidHostname())

    @Test
    fun testInvalidFirstCharHostname() =
        assertFalse("_ostname".isValidHostname())

    @Test
    fun testInvalidCharHostname() =
        assertFalse("h_stname".isValidHostname())

    @Test
    fun testValidHostname() =
        assertTrue("host-name".isValidHostname())

    @Test
    fun testInvalidSegmentCountIpv4() =
        assertFalse("127.0.1".isIpv4())

    @Test
    fun testInvalidTextIpv4() =
        assertFalse("x.0.0.1".isIpv4())

    @Test
    fun testInvalidRangeIpv4() =
        assertFalse("511.0.0.1".isIpv4())

    @Test
    fun testValidIpv4() =
        assertTrue("127.0.0.1".isIpv4())

    @Test
    fun testInvalidCharsIpv6() =
        assertFalse("::x".isIpv6())

    @Test
    fun testInvalidSegmentCountIpv6() =
        assertFalse("fe80:fe80:fe80:fe80:fe80:fe80:fe80:fe80:1".isIpv6())

    @Test
    fun testInvalidSegmentCountDualIpv6() =
        assertFalse("fe80:fe80:fe80:fe80:fe80:fe80:fe80:127.0.0.1".isIpv6())

    @Test
    fun testInvalidEncodedScopeId() =
        assertFalse("::1%25bad%".isIpv6())

    @Test
    fun testValidExplicitIpv6() =
        assertTrue("fe80:fe80:fe80:fe80:fe80:fe80:fe80:fe80".isIpv6())

    @Test
    fun testValidLeadingImplicitIpv6() =
        assertTrue("::1".isIpv6())

    @Test
    fun testValidEmbeddedImplicitIpv6() =
        assertTrue("fe80::26ae:1".isIpv6())

    @Test
    fun testValidTrailingImplicitIpv6() =
        assertTrue("1:fe80::".isIpv6())

    @Test
    fun testValidFullImplicitIpv6() =
        assertTrue("::".isIpv6())

    @Test
    fun testValidExplicitDualIpv6() =
        assertTrue("fe80:fe80:fe80:fe80:fe80:fe80:127.0.0.1".isIpv6())

    @Test
    fun testValidLeadingImplicitDualIpv6() =
        assertTrue("::1:127.0.0.1".isIpv6())

    @Test
    fun testValidEmbeddedImplicitDualIpv6() =
        assertTrue("fe80::1:127.0.0.1".isIpv6())

    @Test
    fun testValidTrailingImplicitDualIpv6() =
        assertTrue("fe80::127.0.0.1".isIpv6())

    @Test
    fun testValidFullImplicitDualIpv6() =
        assertTrue("::127.0.0.1".isIpv6())

    @Test
    fun testValidScopeIdIpv6() =
        assertTrue("::1%25enpos0".isIpv6())
}
