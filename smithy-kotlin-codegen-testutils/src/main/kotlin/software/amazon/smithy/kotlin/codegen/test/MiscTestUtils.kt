/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.test

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.matchers.string.shouldNotContainOnlyOnce
import kotlin.test.assertNotNull

// This file houses miscellaneous test functions that do not fall under other test categories specified in this package.

/** Generate an IDE diff in the case of a test assertion failure. */
fun String?.shouldContainOnlyOnceWithDiff(expected: String) {
    try {
        this.shouldContainOnlyOnce(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

/** Generate an IDE diff in the case of a test assertion failure. */
fun String?.shouldContainWithDiff(expected: String) {
    try {
        this.shouldContain(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

fun String?.shouldNotContainOnlyOnceWithDiff(expected: String) {
    try {
        this.shouldNotContainOnlyOnce(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

fun String.shouldContain(expectedStart: String, expectedEnd: String) {
    val startLines = expectedStart.lines()
    val endLines = expectedEnd.lines()
    val actualLines = lines()

    val startIndex = actualLines.indexOfSublistOrNull(startLines)
    assertNotNull(startIndex, "Cannot find $expectedStart in $this")

    val endIndex = actualLines.indexOfSublistOrNull(endLines, startIndex + startLines.size)
    assertNotNull(endIndex, "Cannot find $expectedEnd after $expectedStart in $this")
}

fun <T> List<T>.indexOfSublistOrNull(sublist: List<T>, startFrom: Int = 0): Int? =
    drop(startFrom).windowed(sublist.size).indexOf(sublist)

/** Format a multi-line string suitable for comparison with codegen, defaults to one level of indention. */
fun String.formatForTest(indent: String = "    ") =
    trimIndent()
        .prependIndent(indent)
        .split('\n')
        .map { if (it.isBlank()) "" else it }
        .joinToString(separator = "\n") { it }

fun String.stripCodegenPrefix(packageName: String = "test"): String {
    val packageDirective = "package $packageName"
    return this.substring(this.indexOf(packageDirective) + packageDirective.length).trim()
}
