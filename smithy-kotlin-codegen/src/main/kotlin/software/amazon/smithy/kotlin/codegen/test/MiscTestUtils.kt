/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.test

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce

/**
 * This file houses miscellaneous test functions that do not fall under other
 * test categories specified in this package.
 */

// Will generate an IDE diff in the case of a test assertion failure.
fun String?.shouldContainOnlyOnceWithDiff(expected: String) {
    try {
        this.shouldContainOnlyOnce(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

// Will generate an IDE diff in the case of a test assertion failure.
internal fun String?.shouldContainWithDiff(expected: String) {
    try {
        this.shouldContain(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

// Format a multi-line string suitable for comparison with codegen, defaults to one level of indention.
fun String.formatForTest(indent: String = "    ") =
    trimIndent()
        .prependIndent(indent)
        .split('\n')
        .map { if (it.isBlank()) "" else it }
        .joinToString(separator = "\n") { it }

internal fun String.stripCodegenPrefix() =
    this.substring(this.indexOf("package test") + "package test".length).trim()
