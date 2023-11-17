/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding
import kotlin.test.*

class UrlParsingTest {
    @Test
    fun testScheme() {
        assertEquals(Scheme.HTTP, Url.parse("http://host").scheme)
        assertEquals(Scheme.HTTPS, Url.parse("https://host").scheme)
        assertEquals(Scheme.WS, Url.parse("ws://host").scheme)
        assertEquals(Scheme.WSS, Url.parse("wss://host").scheme)
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
        assertEquals(Host.parse("127.0.0.1"), Url.parse("https://127.0.0.1").host)
        assertEquals(Host.parse("127.0.00.1"), Url.parse("https://127.0.00.1").host)
        assertEquals(Host.parse("255.192.64.0"), Url.parse("https://255.192.64.0").host)
    }

    @Test
    fun testHostIPv6() {
        assertEquals(Host.parse("::"), Url.parse("https://[::]").host)
        assertEquals(Host.parse("1:1:1:1:1:1:1:1"), Url.parse("https://[1:1:1:1:1:1:1:1]").host)

        assertEquals(Host.parse("1::"), Url.parse("https://[1::]").host)
        assertEquals(Host.parse("1:1::"), Url.parse("https://[1:1::]").host)
        assertEquals(Host.parse("1:1:1::"), Url.parse("https://[1:1:1::]").host)
        assertEquals(Host.parse("1:1:1:1::"), Url.parse("https://[1:1:1:1::]").host)
        assertEquals(Host.parse("1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1::]").host)
        assertEquals(Host.parse("1:1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1:1::]").host)
        assertEquals(Host.parse("1:1:1:1:1:1:1::"), Url.parse("https://[1:1:1:1:1:1:1::]").host)

        assertEquals(Host.parse("::1"), Url.parse("https://[::1]").host)
        assertEquals(Host.parse("::1:1"), Url.parse("https://[::1:1]").host)
        assertEquals(Host.parse("::1:1:1"), Url.parse("https://[::1:1:1]").host)
        assertEquals(Host.parse("::1:1:1:1"), Url.parse("https://[::1:1:1:1]").host)
        assertEquals(Host.parse("::1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1]").host)
        assertEquals(Host.parse("::1:1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1:1]").host)
        assertEquals(Host.parse("::1:1:1:1:1:1:1"), Url.parse("https://[::1:1:1:1:1:1:1]").host)
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
        assertEquals(Scheme.HTTP.defaultPort, Url.parse("http://host").port)
        assertEquals(Scheme.HTTPS.defaultPort, Url.parse("https://host").port)
        assertEquals(Scheme.WS.defaultPort, Url.parse("ws://host").port)
        assertEquals(Scheme.WSS.defaultPort, Url.parse("wss://host").port)

        assertEquals(4433, Url.parse("https://host:4433").port)
        assertEquals(4433, Url.parse("https://192.168.0.1:4433").port)
        assertEquals(4433, Url.parse("https://[::]:4433").port)
    }

    @Test
    fun testNoPath() {
        assertEquals("", Url.parse("https://host").path.toString())
        assertEquals("", Url.parse("https://host?").path.toString())
        assertEquals("", Url.parse("https://host#").path.toString())
        assertEquals("", Url.parse("https://host?#").path.toString())
    }

    @Test
    fun testPath() {
        assertEquals("/path", Url.parse("https://host/path").path.toString())
        assertEquals("/path", Url.parse("https://host:80/path").path.toString())
        assertEquals("/path%2Fsuffix", Url.parse("https://host:80/path%2Fsuffix").path.toString())
        assertEquals("/path%252Fsuffix", Url.parse("https://host:80/path%2Fsuffix", UrlEncoding.None).path.toString())

        // Empty paths with a trailing slash
        assertEquals("/", Url.parse("https://host/").path.toString())
        assertEquals("/", Url.parse("https://host/?").path.toString())
        assertEquals("/", Url.parse("https://host/#").path.toString())
        assertEquals("/", Url.parse("https://host/?#").path.toString())
    }

    @Test
    fun testNoQuery() {
        listOf(
            "https://host",
            "https://host/",
            "https://host#",
            "https://host/#",
        ).forEach { url ->
            val parsed = Url.parse(url).parameters
            assertEquals(0, parsed.size)
            assertFalse(parsed.forceQuery, "Expected forceQuery=false for $url")
        }
    }

    @Test
    fun testQuery() {
        assertEquals(
            QueryParameters { decodedParameters { add("k", "v") } },
            Url.parse("https://host?k=v").parameters,
        )
        assertEquals(
            QueryParameters { decodedParameters { add("k", "v") } },
            Url.parse("https://host/path?k=v").parameters,
        )
        assertEquals(
            QueryParameters { decodedParameters { add("k", "v") } },
            Url.parse("https://host?k=v#fragment").parameters,
        )
        assertEquals(
            QueryParameters { decodedParameters { add("k", "v") } },
            Url.parse("https://host/path?k=v#fragment").parameters,
        )

        assertEquals(
            QueryParameters {
                decodedParameters {
                    addAll("k", listOf("v", "v"))
                    addAll("k2", listOf("v 2", "v&2"))
                    addAll("k 3", listOf("v3"))
                }
            },
            Url.parse("https://host/path?k=v&k=v&k2=v%202&k2=v%262&k%203=v3#fragment").parameters,
        )

        assertEquals(
            QueryParameters {
                decodedParameters {
                    addAll("k", listOf("v", "v"))
                    addAll("k2", listOf("v%202", "v%262"))
                    addAll("k%203", listOf("v3"))
                }
            },
            Url.parse("https://host/path?k=v&k=v&k2=v%202&k2=v%262&k%203=v3#fragment", UrlEncoding.None).parameters,
        )

        // No query parameters but an empty query string (i.e., forceQuery)
        listOf(
            "https://host?",
            "https://host/?",
            "https://host?#",
            "https://host/?#",
        ).forEach { url ->
            val parsed = Url.parse(url).parameters
            assertEquals(0, parsed.size)
            assertTrue(parsed.forceQuery, "Expected forceQuery=true for $url")
        }
    }

    @Test
    fun testNoFragment() {
        assertNull(Url.parse("https://host").fragment)
        assertNull(Url.parse("https://host/").fragment)
        assertNull(Url.parse("https://host?").fragment)
        assertNull(Url.parse("https://host/?").fragment)
    }

    @Test
    fun testFragment() {
        assertEquals("frag&5ment", Url.parse("https://host#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host?#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/?#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/path#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/path?#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host?k=v#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/?k=v#frag%265ment").fragment!!.decoded)
        assertEquals("frag&5ment", Url.parse("https://host/path?k=v#frag%265ment").fragment!!.decoded)

        assertEquals(
            "frag%265ment",
            Url.parse("https://host/path?k=v#frag%265ment", UrlEncoding.None).fragment!!.decoded,
        )

        // No fragment text but a fragment separator (i.e., `#`)
        assertEquals("", Url.parse("https://host#").fragment!!.decoded)
        assertEquals("", Url.parse("https://host/#").fragment!!.decoded)
        assertEquals("", Url.parse("https://host?#").fragment!!.decoded)
        assertEquals("", Url.parse("https://host/?#").fragment!!.decoded)
    }

    @Test
    fun testUserInfo() {
        val expected = UserInfo {
            decodedUserName = "user:"
            decodedPassword = "pass@"
        }
        assertEquals(expected, Url.parse("https://user%3A:pass%40@host").userInfo)
    }

    @Test
    fun testComplete() {
        val expected = Url {
            scheme = Scheme.HTTPS
            userInfo {
                decodedUserName = "userinfo user"
                decodedPassword = "userinfo pass"
            }
            host = Host.Domain("hostname.info")
            port = 4433
            path.decoded = "/pa th"
            parameters.decodedParameters.add("query", "val ue")
            decodedFragment = "frag ment"
        }
        val actual = Url.parse("https://userinfo%20user:userinfo%20pass@hostname.info:4433/pa%20th?query=val%20ue#frag%20ment")
        assertEquals(expected, actual)
    }
}
