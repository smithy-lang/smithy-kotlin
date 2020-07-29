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
package software.aws.clientrt.http.util

import kotlin.test.Test
import kotlin.test.assertEquals

data class EscapeTest(val input: String, val expected: String, val formUrlEncode: Boolean = false)

class TextTest {
    @Test
    fun `url values encode correctly`() {
        val nonEncodedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val encodedCharactersInput = "\t\n\r !\"#$%&'()*+,/:;<=>?@[\\]^`{|}"
        val encodedCharactersOutput =
            "%09%0A%0D%20%21%22%23%24%25%26%27%28%29%2A%2B%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%5E%60%7B%7C%7D"

        val tests: List<EscapeTest> = listOf(
            EscapeTest("", ""),
            EscapeTest("abc", "abc"),
            EscapeTest("a b", "a%20b"),
            EscapeTest("a b", "a+b", formUrlEncode = true),
            EscapeTest("10%", "10%25"),
            EscapeTest(nonEncodedCharacters, nonEncodedCharacters),
            EscapeTest(encodedCharactersInput, encodedCharactersOutput)
        )

        for (test in tests) {
            val actual = test.input.urlEncodeComponent(test.formUrlEncode)
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }

    @Test
    fun `form data values encode correctly`() {
        val nonEncodedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_."
        val encodedCharactersInput = "\t\n\r !\"#$%&'()+,/:;<=>?@[\\]^`{|}"
        val encodedCharactersOutput =
            "%09%0A%0D+%21%22%23%24%25%26%27%28%29%2B%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%5E%60%7B%7C%7D"

        val tests: List<EscapeTest> = listOf(
            EscapeTest("", ""),
            EscapeTest("a b", "a+b"),
            EscapeTest(nonEncodedCharacters, nonEncodedCharacters),
            EscapeTest(encodedCharactersInput, encodedCharactersOutput)
        )

        for (test in tests) {
            val actual = test.input.urlEncodeComponent(true)
            assertEquals(test.expected, actual, "expected ${test.expected}; got: $actual")
        }
    }

    @Test
    fun `url path values encode correctly`() {
        val urlPath = "/wikipedia/en/6/61/Purdue_University_\u2013seal.svg"
        assertEquals("/wikipedia/en/6/61/Purdue_University_%E2%80%93seal.svg", urlPath.encodeUrlPath())
        assertEquals("/kotlin/Tue,%2029%20Apr%202014%2018:30:38%20GMT", "/kotlin/Tue, 29 Apr 2014 18:30:38 GMT".encodeUrlPath())
    }

    @Test
    fun `utf8 url path values encode correctly`() {
        val swissAndGerman = "\u0047\u0072\u00fc\u0065\u007a\u0069\u005f\u007a\u00e4\u006d\u00e4"
        val russian = "\u0412\u0441\u0435\u043c\u005f\u043f\u0440\u0438\u0432\u0435\u0442"
        val japanese = "\u3053\u3093\u306b\u3061\u306f"
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", russian.encodeUrlPath())
        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", swissAndGerman.encodeUrlPath())
        assertEquals("%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF", japanese.encodeUrlPath())
    }
}
