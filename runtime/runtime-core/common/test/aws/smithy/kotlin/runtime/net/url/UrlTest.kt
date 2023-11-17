/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

/*
import kotlin.test.Test
import kotlin.test.fail
import aws.smithy.kotlin.runtime.net.Url as OldUrl
import aws.smithy.kotlin.runtime.net.url.Url as NewUrl

class UrlTest {
    private fun testEquivalence(vararg urls: String) {
        val errors = urls.mapNotNull { url ->
            val old = OldUrl.parse(url).toString()
            val new = NewUrl.parse(url).toString()
            if (old == new) null else "Old: <$old>, New: <$new>"
        }
        if (errors.isNotEmpty()) {
            val message = errors.joinToString("\n", "Found the following errors:\n") { "  $it" }
            fail(message)
        }
    }

    @Test
    fun testEquivalenceOfBasicUrls() {
        testEquivalence(
            "http://amazon.com",
            "https://amazon.com",
        )
    }

    @Test
    fun testEquivalenceOfPorts() {
        testEquivalence(
            "https://amazon.com:8443",
        )
    }

    @Test
    fun testEquivalenceOfPaths() {
        testEquivalence(
            // "https://amazon.com/", // Old parser strips path `/`...is bug?
            "https://amazon.com//",
            "https://amazon.com/foo",
            "https://amazon.com/foo/bar",
            "https://amazon.com/foo/bar/",
            "https://amazon.com/foo//bar/",
            "https://amazon.com/foo/bar//",
        )
    }

    @Test
    fun testEquivalenceOfQueryStrings() {
        testEquivalence(
            // "https://amazon.com?", // Old parser strips '?' with empty query params...is bug?
            "https://amazon.com?foo=bar",
            // "https://amazon.com?foo=bar&baz=qux", // Old parser sorts query by key...is bug?
            "https://amazon.com?foo=f%F0%9F%98%81o",
            // "https://amazon.com?foo=f+o%20o", // Old parser encodes qparam ' ' to %20 instead of '+'...is bug?
        )
    }

    @Test
    fun testEquivalenceOfFragments() {
        testEquivalence(
            // "https://amazon.com#", // Old parser ignores empty fragments...is bug?
            "https://amazon.com#foo",
            "https://amazon.com#f%F0%9F%98%81o",
        )
    }

    @Test
    fun testEquivalenceOfMixedPathsAndQueryStrings() {
        testEquivalence(
            // "https://amazon.com/?", // Old parser strips '?' with empty query params...is bug?
            // "https://amazon.com/?bar=baz", // Old parser strips path `/`...is bug?
            // "https://amazon.com/foo?", // Old parser strips '?' with empty query params...is bug?
            "https://amazon.com/foo?bar=baz",
        )
    }

    @Test
    fun testEquivalenceOfMixedPathsAndFragments() {
        testEquivalence(
            // "https://amazon.com/#", // Old parser strips path `/` and ignores empty fragments...are bugs?
            // "https://amazon.com/#bar", // Old parser strips path `/`...is bug?
            // "https://amazon.com/foo#", // Old parser ignores empty fragments...is bug?
            "https://amazon.com/foo#bar",
        )
    }

    @Test
    fun testEquivalenceOfMixedQueryStringsAndFragments() {
        testEquivalence(
            // "https://amazon.com?#", // Old parser strips '?' with empty query params and ignores empty fragments...are bugs?
            // "https://amazon.com?#baz", // Old parser strips '?' with empty query params...is bug?
            // "https://amazon.com?foo=bar#", // Old parser ignores empty fragments...is bug?
            "https://amazon.com?foo=bar#baz",
        )
    }

    @Test
    fun testEquivalenceOfMixedPathsAndQueryStringsAndFragments() {
        testEquivalence(
            // "https://amazon.com/?#",
            // "https://amazon.com/?#quux",
            // "https://amazon.com/?baz=qux#",
            // "https://amazon.com/?baz=qux#quux",
            // "https://amazon.com/foo/bar?#",
            // "https://amazon.com/foo/bar?#quux",
            // "https://amazon.com/foo/bar?baz=qux#",
            "https://amazon.com/foo/bar?baz=qux#quux",
        )
    }
}
*/
