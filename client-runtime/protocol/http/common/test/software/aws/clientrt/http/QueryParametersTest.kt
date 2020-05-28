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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryParametersTest {

    @Test
    fun `it builds`() {
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
    fun `it encodes to query string`() {
        data class QueryParamTest(val params: QueryParameters, val expected: String)
        val tests: List<QueryParamTest> = listOf(
            QueryParamTest(
                QueryParameters {
                    append("q", "puppies")
                    append("oe", "utf8")
                },
                "oe=utf8&q=puppies"
            ),
            QueryParamTest(
                QueryParameters {
                    appendAll("q", listOf("dogs", "&", "7"))
                },
                "q=dogs&q=%26&q=7"
            ),
            QueryParamTest(
                QueryParameters {
                    appendAll("a", listOf("a1", "a2", "a3"))
                    appendAll("b", listOf("b1", "b2", "b3"))
                    appendAll("c", listOf("c1", "c2", "c3"))
                },
                "a=a1&a=a2&a=a3&b=b1&b=b2&b=b3&c=c1&c=c2&c=c3"
            )
        )
        for (test in tests) {
            val actual = test.params.urlEncode()
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }
}
