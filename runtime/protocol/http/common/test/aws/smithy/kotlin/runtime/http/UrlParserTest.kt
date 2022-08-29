/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.util.net.Host
import kotlin.test.Test
import kotlin.test.*

class UrlParserTest {
    @Test
    fun testScheme() {
        assertEquals(Protocol.HTTP, Url.parse("http://host").scheme)
        assertEquals(Protocol.HTTPS, Url.parse("https://host").scheme)
        assertEquals(Protocol.WS, Url.parse("ws://host").scheme)
        assertEquals(Protocol.WSS, Url.parse("wss://host").scheme)
    }

    @Test
    fun testHostDomain() {
        assertEquals(Host.Domain("host"), Url.parse("wss://host").host)
        assertEquals(Host.Domain("0host"), Url.parse("wss://0host").host)
        assertEquals(Host.Domain("host.subdomain"), Url.parse("wss://host.subdomain").host)
        assertEquals(Host.Domain("host.0subdomain"), Url.parse("wss://host.0subdomain").host)
        assertEquals(Host.Domain("host.0sub-domain"), Url.parse("wss://host.0sub-domain").host)
        assertEquals(Host.Domain("host.0sub-domain"), Url.parse("wss://host.0sub-domain:8080").host)

        val maxLengthHost = buildString {
            repeat(63) { append('i') }
        }
        assertEquals(Host.Domain(maxLengthHost), Url.parse("wss://$maxLengthHost").host)
        assertEquals(Host.Domain("$maxLengthHost.$maxLengthHost"), Url.parse("wss://$maxLengthHost.$maxLengthHost").host)
    }

    @Test
    fun testHostIPv4() {
        assertEquals(Host.IPv4("127.0.0.1"), Url.parse("https://127.0.0.1").host)
        assertEquals(Host.IPv4("127.0.00.1"), Url.parse("https://127.0.00.1").host)
        assertEquals(Host.IPv4("255.192.64.0"), Url.parse("https://255.192.64.0").host)
    }

    @Test
    fun testHostIPv6() {
        assertEquals(Host.IPv6("::"), Url.parse("https://[::]").host)
        assertEquals(Host.IPv6("1:1:1:1:1:1:1:1"), Url.parse("https://[1:1:1:1:1:1:1:1]").host)

        assertEquals(Host.IPv6("1::"), Url.parse("https://[1::]").host)
        assertEquals(Host.IPv6("1:1::"), Url.parse("https://[1:1::]").host)
        assertEquals(Host.IPv6("1:1:1::"), Url.parse("https://[1:1:1::]").host)
        assertEquals(Host.IPv6("1:1:1:1::"), Url.parse("https://[1:1:1:1::]").host)
        assertEquals(Host.IPv6("1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1::]").host)
        assertEquals(Host.IPv6("1:1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1:1::]").host)
        assertEquals(Host.IPv6("1:1:1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1:1:1::]").host)

        assertEquals(Host.IPv6("::1"), Url.parse("https://[::1]").host)
        assertEquals(Host.IPv6("::1:1"), Url.parse("https://[::1:1]").host)
        assertEquals(Host.IPv6("::1:1:1"), Url.parse("https://[::1:1:1]").host)
        assertEquals(Host.IPv6("::1:1:1:1"), Url.parse("https://[::1:1:1:1]").host)
        assertEquals(Host.IPv6("::1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1]").host)
        assertEquals(Host.IPv6("::1:1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1:1]").host)
        assertEquals(Host.IPv6("::1:1:1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1:1:1]").host)
    }

    @Test
    fun testInvalidHostDomain() {
        assertFailsWith<IllegalArgumentException>("-host is not a valid internet host") {
            Url.parse("https://-host")
        }
        assertFailsWith<IllegalArgumentException>("-host.sub is not a valid internet host") {
            Url.parse("https://-host.sub")
        }
        assertFailsWith<IllegalArgumentException>("host. is not a valid internet host") {
            Url.parse("https://host.")
        }
        assertFailsWith<IllegalArgumentException>(".host is not a valid internet host") {
            Url.parse("https://.host")
        }
        assertFailsWith<IllegalArgumentException>(".host is not a valid internet host") {
            Url.parse("https://.host:443")
        }

        val tooLongHost = buildString {
            repeat(64) { append('i') }
        }
        assertFailsWith<IllegalArgumentException>("$tooLongHost is not a valid internet host") {
            Url.parse("https://$tooLongHost")
        }
        assertFailsWith<IllegalArgumentException>("shortEnough.$tooLongHost is not a valid internet host") {
            Url.parse("https://shortEnough.$tooLongHost")
        }
    }

    @Test
    fun testInvalidHostIPv6() {
        assertFailsWith<IllegalArgumentException>("::: is not a valid internet host") {
            Url.parse("https://[:::]")
        }
        assertFailsWith<IllegalArgumentException>("x: is not a valid internet host") {
            Url.parse("https://[x:]")
        }
        assertFailsWith<IllegalArgumentException>("x: is not a valid internet host") {
            Url.parse("https://[x:]:445")
        }
        assertFailsWith<IllegalArgumentException>("fe80:: is not a valid internet host") {
            Url.parse("https://fe80::")
        }
    }

    @Test
    fun testInvalidHostBrackets() {
        assertFailsWith<IllegalArgumentException>("unmatched [ or ]") {
            Url.parse("https://[fe80::")
        }
        assertFailsWith<IllegalArgumentException>("unmatched [ or ]") {
            Url.parse("https://fe80::]")
        }
        assertFailsWith<IllegalArgumentException>("unmatched [ or ]") {
            Url.parse("https://]fe80::[")
        }
        assertFailsWith<IllegalArgumentException>("unexpected characters before [") {
            Url.parse("https://f[e80::]")
        }
        assertFailsWith<IllegalArgumentException>("unexpected characters after [") {
            Url.parse("https://[fe80:]:")
        }
        assertFailsWith<IllegalArgumentException>("non-ipv6 host was enclosed in []-brackets") {
            Url.parse("https://[host]")
        }
    }

    @Test
    fun testPort() {
        assertEquals(Protocol.HTTP.defaultPort, Url.parse("http://host").port)
        assertEquals(Protocol.HTTPS.defaultPort, Url.parse("https://host").port)
        assertEquals(Protocol.WS.defaultPort, Url.parse("ws://host").port)
        assertEquals(Protocol.WSS.defaultPort, Url.parse("wss://host").port)

        assertEquals(4433, Url.parse("https://host:4433").port)
        assertEquals(4433, Url.parse("https://192.168.0.1:4433").port)
        assertEquals(4433, Url.parse("https://[::]:4433").port)
    }

    @Test
    fun testNoPath() {
        assertEquals("", Url.parse("https://host").path)
        assertEquals("", Url.parse("https://host/").path)
        assertEquals("", Url.parse("https://host?").path)
        assertEquals("", Url.parse("https://host#").path)
        assertEquals("", Url.parse("https://host/?").path)
        assertEquals("", Url.parse("https://host/#").path)
        assertEquals("", Url.parse("https://host?#").path)
        assertEquals("", Url.parse("https://host/?#").path)
    }

    @Test
    fun testPath() {
        assertEquals("/path", Url.parse("https://host/path").path)
        assertEquals("/path", Url.parse("https://host:80/path").path)
    }

    @Test
    fun testNoQuery() {
        assertEquals(QueryParameters.Empty, Url.parse("https://host").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host/").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host?").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host#").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host/?").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host/#").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host?#").parameters)
        assertEquals(QueryParameters.Empty, Url.parse("https://host/?#").parameters)
    }

    @Test
    fun testQuery() {
        assertEquals(
            QueryParameters { append("k", "v") },
            Url.parse("https://host?k=v").parameters,
        )
        assertEquals(
            QueryParameters { append("k", "v") },
            Url.parse("https://host/path?k=v").parameters,
        )
        assertEquals(
            QueryParameters { append("k", "v") },
            Url.parse("https://host?k=v#fragment").parameters,
        )
        assertEquals(
            QueryParameters { append("k", "v") },
            Url.parse("https://host/path?k=v#fragment").parameters,
        )

        assertEquals(
            QueryParameters {
                appendAll("k", listOf("v", "v"))
                appendAll("k2", listOf("v 2", "v&2"))
            },
            Url.parse("https://host/path?k=v&k=v&k2=v%202&k2=v%262#fragment").parameters,
        )
    }

    @Test
    fun testNoFragment() {
        assertNull(Url.parse("https://host").fragment)
        assertNull(Url.parse("https://host/").fragment)
        assertNull(Url.parse("https://host?").fragment)
        assertNull(Url.parse("https://host#").fragment)
        assertNull(Url.parse("https://host/?").fragment)
        assertNull(Url.parse("https://host/#").fragment)
        assertNull(Url.parse("https://host?#").fragment)
        assertNull(Url.parse("https://host/?#").fragment)
    }

    @Test
    fun testFragment() {
        assertEquals("frag&5ment", Url.parse("https://host#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host?#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/?#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/path#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/path?#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host?k=v#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/?k=v#frag%265ment").fragment)
        assertEquals("frag&5ment", Url.parse("https://host/path?k=v#frag%265ment").fragment)
    }

    @Test
    fun testUserInfo() {
        assertEquals(UserInfo("user:", "pass@"), Url.parse("https://user%3A:pass%40@host").userInfo)
    }

    @Test
    fun testComplete() {
        val expected = UrlBuilder {
            scheme = Protocol.HTTPS
            userInfo = UserInfo("userinfo user", "userinfo pass")
            host = Host.Domain("hostname.info")
            port = 4433
            path = "/pa th"
            parameters.append("query", "val ue")
            fragment = "frag ment"
        }
        val actual = Url.parse("https://userinfo%20user:userinfo%20pass@hostname.info:4433/pa%20th?query=val%20ue#frag%20ment")
        assertEquals(expected, actual)
    }
}