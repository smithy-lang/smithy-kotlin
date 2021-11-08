/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.test

import kotlin.test.assertEquals

/**
 * This file houses test functions specific to Kotlin language particulars.
 */

internal fun String.assertBalancedBracesAndParens() {
    // sanity check since we are testing fragments
    var openBraces = 0
    var closedBraces = 0
    var openParens = 0
    var closedParens = 0
    this.forEach {
        when (it) {
            '{' -> openBraces++
            '}' -> closedBraces++
            '(' -> openParens++
            ')' -> closedParens++
        }
    }
    assertEquals(openBraces, closedBraces, "unmatched open/closed braces:\n$this")
    assertEquals(openParens, closedParens, "unmatched open/close parens:\n$this")
}
