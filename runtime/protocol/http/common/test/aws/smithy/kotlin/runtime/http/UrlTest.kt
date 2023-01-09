/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.net.Host
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class UrlTest {
    @Test
    fun basicToString() {
        val expected = "https://test.aws.com/kotlin"
        val url = Url(
            Protocol.HTTPS,
            Host.Domain("test.aws.com"),
            path = "/kotlin",
        )
        assertEquals(expected, url.toString())
    }

    @Test
    fun forceRetainQuery() {
        val expected = "https://test.aws.com/kotlin?"
        val url = UrlBuilder {
            host = Host.Domain("test.aws.com")
            path = "/kotlin"
            forceQuery = true
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun withParameters() {
        val expected = "https://test.aws.com/kotlin?baz=quux&baz=qux&foo=bar"
        val params = QueryParameters {
            append("foo", "bar")
            appendAll("baz", listOf("quux", "qux"))
        }

        val url = Url(
            Protocol.HTTPS,
            Host.Domain("test.aws.com"),
            path = "/kotlin",
            parameters = params,
        )
        assertEquals(expected, url.toString())
    }

    @Test
    fun specificPort() {
        val expected = "https://test.aws.com:8000"
        val url = Url(
            Protocol.HTTPS,
            Host.Domain("test.aws.com"),
            port = 8000,
        )
        assertEquals(expected, url.toString())

        val expected2 = "http://test.aws.com"
        val url2 = Url(
            Protocol.HTTP,
            Host.Domain("test.aws.com"),
            port = 80,
        )
        assertEquals(expected2, url2.toString())
    }

    @Test
    fun portRange() {
        fun checkPort(n: Int) {
            assertEquals(
                n,
                Url(
                    Protocol.HTTPS,
                    Host.Domain("test.aws.com"),
                    port = n,
                ).port,
            )
        }

        checkPort(1)
        checkPort(65535)
        assertFails {
            checkPort(65536)
        }
    }

    @Test
    fun userinfoNoPassword() {
        val expected = "https://user@test.aws.com"
        val url = UrlBuilder {
            scheme = Protocol.HTTPS
            host = Host.Domain("test.aws.com")
            userInfo = UserInfo("user", "")
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun fullUserinfo() {
        val expected = "https://user:password@test.aws.com"
        val url = UrlBuilder {
            scheme = Protocol.HTTPS
            host = Host.Domain("test.aws.com")
            userInfo = UserInfo("user", "password")
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun itBuilds() {
        val builder = UrlBuilder()
        builder.scheme = Protocol.HTTP
        builder.host = Host.Domain("test.aws.com")
        builder.path = "/kotlin"
        val url = builder.build()
        val expected = "http://test.aws.com/kotlin"
        assertEquals(expected, url.toString())
        assertEquals(Protocol.HTTP, builder.scheme)
        assertEquals(Host.Domain("test.aws.com"), builder.host)
        assertEquals(null, builder.port)
        assertEquals(null, builder.fragment)
        assertEquals(null, builder.userInfo)
    }

    @Test
    fun itBuildsWithNonDefaultPort() {
        val url = UrlBuilder {
            scheme = Protocol.HTTP
            host = Host.Domain("test.aws.com")
            path = "/kotlin"
            port = 3000
        }
        val expected = "http://test.aws.com:3000/kotlin"
        assertEquals(expected, url.toString())
    }

    @Test
    fun itBuildsWithParameters() {
        val url = UrlBuilder {
            scheme = Protocol.HTTP
            host = Host.Domain("test.aws.com")
            path = "/kotlin"
            parameters {
                append("foo", "baz")
            }
        }
        val expected = "http://test.aws.com/kotlin?foo=baz"
        assertEquals(expected, url.toString())
    }

    fun testEncodePath() {
        val url = UrlBuilder()
        url.parameters {
            appendAll("q", listOf("dogs", "&", "7"))
            append("empty", "")
        }
        url.path = "/foo/bar"
        url.fragment = "header1"
        val expected = "/foo/bar?empty=&q=dogs&q=%26&q=7#header1"
        assertEquals(expected, url.encodedPath)
        assertEquals(expected, url.build().encodedPath)

        val noParams = UrlBuilder {
            path = "/foo/bar"
        }
        assertEquals("/foo/bar", noParams.encodedPath)
    }

    @Test
    fun testDeepCopy() {
        val builder1 = UrlBuilder().apply {
            host = Host.Domain("foo.com")
            port = 1234
            parameters {
                append("a", "alligator")
                append("b", "bear")
            }
        }

        val builder2 = builder1.deepCopy()

        builder1.host = Host.Domain("bar.org")
        builder1.port = 4321
        builder1.parameters.append("c", "chinchilla")

        val url1 = builder1.build().toString()
        assertEquals("https://bar.org:4321?a=alligator&b=bear&c=chinchilla", url1)
        val url2 = builder2.build().toString()
        assertEquals("https://foo.com:1234?a=alligator&b=bear", url2)
    }
}
