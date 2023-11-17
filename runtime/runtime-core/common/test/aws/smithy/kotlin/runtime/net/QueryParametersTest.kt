/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

/*
import kotlin.test.*

class QueryParametersTest {

    @Test
    fun itBuilds() {
        val params = QueryParameters {
            append("foo", "baz")
            appendAll("foo", listOf("bar", "quux"))
            append("qux", "john")
            remove("qux")
        }
        assertEquals(params.getAll("foo"), listOf("baz", "bar", "quux"))
        assertTrue(params.contains("foo", "quux"))
        assertFalse(params.contains("qux"))
        params.forEach { name, values ->
            when (name) {
                "foo" -> assertEquals(values, listOf("baz", "bar", "quux"))
            }
        }
    }

    @Test
    fun itEncodesToQueryString() {
        data class QueryParamTest(val params: QueryParameters, val expected: String)
        val tests: List<QueryParamTest> = listOf(
            QueryParamTest(
                QueryParameters {
                    append("q", "puppies")
                    append("oe", "utf8")
                },
                "oe=utf8&q=puppies",
            ),
            QueryParamTest(
                QueryParameters {
                    appendAll("q", listOf("dogs", "&", "7"))
                },
                "q=dogs&q=%26&q=7",
            ),
            QueryParamTest(
                QueryParameters {
                    appendAll("a", listOf("a1", "a2", "a3"))
                    appendAll("b", listOf("b1", "b2", "b3"))
                    appendAll("c", listOf("c1", "c2", "c3"))
                },
                "a=a1&a=a2&a=a3&b=b1&b=b2&b=b3&c=c1&c=c2&c=c3",
            ),
        )
        for (test in tests) {
            val actual = test.params.urlEncode()
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }

    @Test
    fun testSubsequentModificationsDontAffectOriginal() {
        val builder = QueryParametersBuilder()

        builder.append("a", "alligator")
        builder.append("b", "bunny")
        builder.append("c", "chinchilla")
        val first = builder.build()
        val firstExpected = mapOf(
            "a" to listOf("alligator"),
            "b" to listOf("bunny"),
            "c" to listOf("chinchilla"),
        )

        builder.append("a", "anteater")
        builder.remove("b")
        builder["c"] = "crocodile"
        val second = builder.build()
        val secondExpected = mapOf(
            "a" to listOf("alligator", "anteater"),
            "c" to listOf("crocodile"),
        )

        assertEquals(firstExpected.entries, first.entries())
        assertEquals(secondExpected.entries, second.entries())
    }

    @Test
    fun splitQueryStringIntoParts() {
        val query = "foo=baz&bar=quux&foo=qux&a="
        val actual = query.splitAsQueryParameters()
        val expected = QueryParameters {
            append("foo", "baz")
            append("foo", "qux")
            append("bar", "quux")
            append("a", "")
        }

        expected.entries().forEach { entry ->
            entry.value.forEach { value ->
                assertTrue(actual.contains(entry.key, value), "parsed query does not contain ${entry.key}:$value")
            }
        }

        val queryNoEquals = "abc=cde&noequalssign"
        val actualNoEquals = queryNoEquals.splitAsQueryParameters()
        val expectedNoEquals = QueryParameters {
            append("abc", "cde")
            append("noequalssign", "")
        }
        expectedNoEquals.entries().forEach { entry ->
            entry.value.forEach { value ->
                assertTrue(actualNoEquals.contains(entry.key, value), "parsed query does not contain ${entry.key}:$value")
            }
        }
    }

    @Test
    fun testFullUriToQueryParameters() {
        val uri = "http://foo.com?a=apple&b=banana&c=cherry"
        val params = uri.fullUriToQueryParameters()
        assertNotNull(params)
        assertEquals("apple", params["a"])
        assertEquals("banana", params["b"])
        assertEquals("cherry", params["c"])
    }

    @Test
    fun testFullUriToQueryParameters_withFragment() {
        val uri = "http://foo.com?a=apple&b=banana&c=cherry#d=durian"
        val params = uri.fullUriToQueryParameters()
        assertNotNull(params)
        assertEquals("apple", params["a"])
        assertEquals("banana", params["b"])
        assertEquals("cherry", params["c"])
        assertFalse("d" in params)
    }

    @Test
    fun testFullUriToQueryParameters_emptyQueryString() {
        val uri = "http://foo.com?"
        val params = uri.fullUriToQueryParameters()
        assertNull(params)
    }

    @Test
    fun testFullUriToQueryParameters_noQueryString() {
        val uri = "http://foo.com"
        val params = uri.fullUriToQueryParameters()
        assertNull(params)
    }
}
*/