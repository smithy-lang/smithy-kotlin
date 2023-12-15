/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlPathTest {
    @Test
    fun testTrailingSlash() {
        val path = UrlPath.parseEncoded("/")
        assertEquals(0, path.segments.size)
        assertTrue(path.trailingSlash)
        assertEquals("/", path.toString())
    }

    @Test
    fun testNormalize() {
        mapOf(
            "" to "/",
            "/" to "/",
            "foo" to "/foo",
            "/foo" to "/foo",
            "foo/" to "/foo/",
            "/foo/" to "/foo/",
            "/a/b/c" to "/a/b/c",
            "/a/b/../c" to "/a/c",
            "/a/./c" to "/a/c",
            "/./" to "/",
            "/a/b/./../c" to "/a/c",
            "/a/b/c/d/../e/../../f/../../../g" to "/g",
            "//a//b//c//" to "/a/b/c/",
        ).forEach { (unnormalized, expected) ->
            val actual = UrlPath {
                encoded = unnormalized
                normalize()
            }.toString()

            assertEquals(expected, actual, "Unexpected normalization for '$unnormalized'")
        }
    }

    @Test
    fun testNormalizeError() {
        UrlPath {
            encoded = "/a/b/../../.."

            assertFailsWith<IllegalStateException> { normalize() }
        }
    }
}
