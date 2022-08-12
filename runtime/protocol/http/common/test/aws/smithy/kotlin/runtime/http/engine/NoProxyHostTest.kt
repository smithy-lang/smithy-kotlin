/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class NoProxyHostTest {

    private data class TestCase(
        val noProxyHost: String,
        val url: String,
        val shouldMatch: Boolean,
        val noProxyPort: Int? = null,
    )

    private val tests = listOf(
        // any
        TestCase("*", "https://example.com", true),
        // exact
        TestCase("example.com", "https://example.com", true),
        // subdomain
        TestCase("example.com", "https://foo.example.com", true),
        // not-subdomain
        TestCase("example.com", "https://notexample.com", false),
        // no match
        TestCase("example.com", "https://example.net", false),
        // implicit different port
        TestCase("example.com", "https://example.com", false, noProxyPort = 8080),
        // explicit different port
        TestCase("example.com", "https://example.com:9000", false, noProxyPort = 8080),
        // implicit same port
        TestCase("example.com", "https://example.com", true, noProxyPort = 443),
        // explicit same port
        TestCase("example.com", "https://example.com:8080", true, noProxyPort = 8080),
        // IPv6
        TestCase("2001:db8::1", "https://[2001:db8::1]", true),
        TestCase("2001:db8::1", "https://[db8::1]", false),
    )

    @Test
    fun testMatches() {
        tests.forEachIndexed { i, testCase ->
            val noProxyHost = NoProxyHost(testCase.noProxyHost, testCase.noProxyPort)
            val url = Url.parse(testCase.url)

            assertEquals(testCase.shouldMatch, noProxyHost.matches(url), "[idx=$i] expected $noProxyHost to match $url")
        }
    }
}
