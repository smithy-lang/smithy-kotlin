/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text

import kotlin.test.Test
import kotlin.test.assertEquals

class ScannerTest {
    @Test
    fun testUpToOrEnd() {
        val text = "abc?123:xyz/789"
        val scanner = Scanner(text)

        // To the '?'
        scanner.upToOrEnd("?", ":", "/") {
            assertEquals("abc", it)
        }

        // At the '?' so no advancement, process an empty string
        scanner.upToOrEnd("?", ":", "/") {
            assertEquals("", it)
        }

        // To the ':'
        scanner.upToOrEnd(":", "/") {
            assertEquals("?123", it)
        }

        // To the '/'
        scanner.upToOrEnd("/") {
            assertEquals(":xyz", it)
        }

        // To the end (because '?' doesn't appear again)
        scanner.upToOrEnd("?") {
            assertEquals("/789", it)
        }

        // At the end, nothing more to scan
        scanner.upToOrEnd("?", ":", "/") {
            assertEquals("", it)
        }
    }
}
