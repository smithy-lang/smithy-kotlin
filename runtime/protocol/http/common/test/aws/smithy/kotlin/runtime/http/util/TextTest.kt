/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.util

import aws.smithy.kotlin.runtime.text.urlEncodeComponent
import kotlin.test.*

class TextTest {
    @Test
    fun encodeLabels() {
        assertEquals("a%2Fb", "a/b".encodeLabel())
        assertEquals("a/b", "a/b".encodeLabel(greedy = true))
    }

    @Test
    fun encodeReservedChars() {
        // verify that both httpLabel and httpQuery bound components encode characters from the reserved
        // set of characters in section 2.2
        val input = ":/?#[]@!$&'()*+,;=% "
        val expected = "%3A%2F%3F%23%5B%5D%40%21%24%26%27%28%29%2A%2B%2C%3B%3D%25%20"
        assertEquals(expected, input.encodeLabel())
        assertEquals(expected, input.urlEncodeComponent())
    }

    @Test
    fun encodesPercent() {
        // verify that something that looks percent encoded actually encodes the percent. label/query should always
        // be going from raw -> encoded. Users should not be percent-encoding values passed to the runtime
        val input = "%25"
        val expected = "%2525"
        assertEquals(expected, input.encodeLabel())
        assertEquals(expected, input.urlEncodeComponent())
    }
}
