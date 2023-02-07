/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class CaseUtilsTest {
    @Test
    fun `it should invert the case of a character`() {
        val tests = mapOf(
            'a' to 'A',
            'A' to 'a',
            '!' to '!',
        )
        tests.forEach { (start, end) ->
            assertEquals(end, start.toggleCase(), "$start toggled should've been $end")
        }
    }

    @Test
    fun `it should invert the case of a string's first character`() {
        val tests = mapOf(
            "apple" to "Apple",
            "Apple" to "apple",
            "!" to "!",
        )
        tests.forEach { (start, end) ->
            assertEquals(end, start.toggleFirstCharacterCase(), "$start toggled should've been $end")
        }
    }
}
