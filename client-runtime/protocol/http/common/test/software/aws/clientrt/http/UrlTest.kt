/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class UrlTest {
    @Test
    fun `basic to string`() {
        val expected = "https://test.aws.com/kotlin"
        val url = Url(
            Protocol.HTTPS,
            "test.aws.com",
            path = "/kotlin"
        )
        assertEquals(expected, url.toString())
    }

    @Test
    fun `force retain query`() {
        val expected = "https://test.aws.com/kotlin?"
        val url = UrlBuilder {
            host = "test.aws.com"
            path = "/kotlin"
            forceQuery = true
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun `with parameters`() {
        val expected = "https://test.aws.com/kotlin?baz=quux&baz=qux&foo=bar"
        val params = QueryParameters {
            append("foo", "bar")
            appendAll("baz", listOf("quux", "qux"))
        }

        val url = Url(
            Protocol.HTTPS,
            "test.aws.com",
            path = "/kotlin",
            parameters = params
        )
        assertEquals(expected, url.toString())
    }

    @Test
    fun `specific port`() {
        val expected = "https://test.aws.com:8000"
        val url = Url(
            Protocol.HTTPS,
            "test.aws.com",
            port = 8000
        )
        assertEquals(expected, url.toString())

        val expected2 = "http://test.aws.com"
        val url2 = Url(
            Protocol.HTTP,
            "test.aws.com",
            port = 80
        )
        assertEquals(expected2, url2.toString())
    }

    @Test
    fun `port range`() {
        fun checkPort(n: Int) {
            assertEquals(n, Url(
                Protocol.HTTPS,
                "test.aws.com",
                port = n
            ).port)
        }

        checkPort(1)
        checkPort(65536)
        assertFails {
            checkPort(65537)
        }
    }

    @Test
    fun `userinfo no password`() {
        val expected = "https://user@test.aws.com"
        val url = UrlBuilder {
            scheme = Protocol.HTTPS
            host = "test.aws.com"
            userInfo = UserInfo("user", "")
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun `full userinfo`() {
        val expected = "https://user:password@test.aws.com"
        val url = UrlBuilder {
            scheme = Protocol.HTTPS
            host = "test.aws.com"
            userInfo = UserInfo("user", "password")
        }
        assertEquals(expected, url.toString())
    }

    @Test
    fun `it builds`() {
        val builder = UrlBuilder()
        builder.scheme = Protocol.HTTP
        builder.host = "test.aws.com"
        builder.path = "/kotlin"
        val url = builder.build()
        val expected = "http://test.aws.com/kotlin"
        assertEquals(expected, url.toString())
        assertEquals(Protocol.HTTP, builder.scheme)
        assertEquals("test.aws.com", builder.host)
        assertEquals(null, builder.port)
        assertEquals(null, builder.fragment)
        assertEquals(null, builder.userInfo)
    }

    @Test
    fun `it builds with non default port`() {
        val url = UrlBuilder {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin"
            port = 3000
        }
        val expected = "http://test.aws.com:3000/kotlin"
        assertEquals(expected, url.toString())
    }

    @Test
    fun `it builds with parameters`() {
        val url = UrlBuilder {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin"
            parameters {
                append("foo", "baz")
            }
        }
        val expected = "http://test.aws.com/kotlin?foo=baz"
        assertEquals(expected, url.toString())
    }
}
