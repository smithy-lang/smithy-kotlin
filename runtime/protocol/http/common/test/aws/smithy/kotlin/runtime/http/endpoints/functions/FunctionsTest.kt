/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.endpoints.functions

import kotlin.test.*

class FunctionsTest {
    @Test
    fun testSubstringInvalidIndices() =
        assertNull(substring("foo", 2, 1, false))

    @Test
    fun testSubstringInvalidEndIndex() =
        assertNull(substring("foo", 0, 4, false))

    @Test
    fun testSubstring() =
        assertEquals("abc", substring("abcde", 0, 3, false))

    @Test
    fun testSubstringReversed() =
        assertEquals("cde", substring("abcde", 0, 3, true))

    @Test
    fun testParseUrlInvalidUrl() =
        assertNull(parseUrl("invalidscheme://"))

    @Test
    fun testParseUrlNoPort() =
        assertEquals(
            Url(
                "http",
                "hostname.com",
                "/path",
                "/path/",
                false
            ),
            parseUrl("http://hostname.com/path")
        )

    @Test
    fun testParseUrlStringAuthority() =
        assertEquals(
            Url(
                "http",
                "hostname.com:4001",
                "/path",
                "/path/",
                false
            ),
            parseUrl("http://hostname.com:4001/path")
        )

    @Test
    fun testParseUrlIpv4Authority() =
        assertEquals(
            Url(
                "http",
                "172.19.0.1:4001",
                "/path",
                "/path/",
                true
            ),
            parseUrl("http://172.19.0.1:4001/path")
        )

    @Test
    fun testParseUrlIpv6Authority() =
        assertEquals(
            Url(
                "http",
                "[::5949]:4001",
                "/path",
                "/path/",
                true
            ),
            parseUrl("http://[::5949]:4001/path")
        )
}
