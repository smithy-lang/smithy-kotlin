/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class HeaderListsTest {

    @Test
    fun testSplitStringList() {
        assertEquals(listOf("foo"), splitHeaderListValues("foo"))

        // trailing space
        assertEquals(listOf("fooTrailing"), splitHeaderListValues("fooTrailing   "))

        // leading and trailing space
        assertEquals(listOf("  foo  "), splitHeaderListValues("\"  foo  \""))

        // ignore spaces between values
        assertEquals(listOf("foo", "bar"), splitHeaderListValues("foo  ,  bar"))
        assertEquals(listOf("foo", "bar"), splitHeaderListValues("\"foo\"  ,  \"bar\""))

        // comma in quotes
        assertEquals(listOf("foo,bar", "baz"), splitHeaderListValues("\"foo,bar\",baz"))

        // comm in quotes w/trailing space
        assertEquals(listOf("foo,bar", "baz"), splitHeaderListValues("\"foo,bar\",baz  "))

        // quote in quotes
        assertEquals(listOf("foo\",bar", "\"asdf\"", "baz"), splitHeaderListValues("\"foo\\\",bar\",\"\\\"asdf\\\"\",baz"))

        // quote in quote w/spaces
        assertEquals(listOf("foo\",bar", "\"asdf  \"", "baz"), splitHeaderListValues("\"foo\\\",bar\", \"\\\"asdf  \\\"\", baz"))

        // empty quotes
        assertEquals(listOf("", "baz"), splitHeaderListValues("\"\",baz"))

        // escaped slashes
        assertEquals(listOf("foo", "(foo\\bar)"), splitHeaderListValues("foo, \"(foo\\\\bar)\""))

        // empty
        assertEquals(listOf("", "1"), splitHeaderListValues(",1"))

        assertFailsWith<IllegalStateException> {
            splitHeaderListValues("foo, bar, \"baz")
        }.message.shouldContain("missing end quote around quoted header value: `baz`")

        assertFailsWith<IllegalStateException> {
            splitHeaderListValues("foo  ,  \"bar\"  \tf,baz")
        }.message.shouldContain("Unexpected char `f` between header values. Previous header: `bar`")
    }

    @Test
    fun testSplitIntList() {
        assertEquals(listOf("1"), splitHeaderListValues("1"))
        assertEquals(listOf("1", "2", "3"), splitHeaderListValues("1,2,3"))
        assertEquals(listOf("1", "2", "3"), splitHeaderListValues("1,  2,  3"))

        // quoted
        assertEquals(listOf("1", "2", "3", "-4", "5"), splitHeaderListValues("1,\"2\",3,\"-4\",5"))
    }

    @Test
    fun testSplitBoolList() {
        assertEquals(listOf("true", "false", "true", "true"), splitHeaderListValues("true,\"false\",true,\"true\""))
    }

    @Test
    fun itSplitsHttpDateLists() {
        // input to expected
        val tests = listOf(
            // no split
            "Mon, 16 Dec 2019 23:48:18 GMT" to listOf("Mon, 16 Dec 2019 23:48:18 GMT"),
            // with split
            "Mon, 16 Dec 2019 23:48:18 GMT, Tue, 17 Dec 2019 23:48:18 GMT" to listOf(
                "Mon, 16 Dec 2019 23:48:18 GMT",
                "Tue, 17 Dec 2019 23:48:18 GMT",
            ),
            // empty
            "" to listOf(""),
        )

        for (test in tests) {
            assertEquals(test.second, splitHttpDateHeaderListValues(test.first))
        }

        val ex = assertFails {
            splitHttpDateHeaderListValues("Mon, 16 Dec 2019 23:48:18 GMT, , Tue, 17 Dec 2019 23:48:18 GMT")
        }
        ex.message!!.shouldContain("invalid timestamp HttpDate header comma separations: `Mon")
    }

    @Test
    fun itQuotesHeaderValues() {
        assertEquals("", quoteHeaderValue(""))
        assertEquals("foo", quoteHeaderValue("foo"))
        assertEquals("\"  foo\"", quoteHeaderValue("  foo"))
        assertEquals("foo bar", quoteHeaderValue("foo bar"))
        assertEquals("\"foo,bar\"", quoteHeaderValue("foo,bar"))
        assertEquals("\",\"", quoteHeaderValue(","))
        assertEquals("\"\\\"foo\\\"\"", quoteHeaderValue("\"foo\""))
        assertEquals("\"\\\"f\\\\oo\\\"\"", quoteHeaderValue("\"f\\oo\""))
        assertEquals("\"(\"", quoteHeaderValue("("))
        assertEquals("\")\"", quoteHeaderValue(")"))
    }
}
