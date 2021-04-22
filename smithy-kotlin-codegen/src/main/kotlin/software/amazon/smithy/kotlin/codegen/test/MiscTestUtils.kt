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
fun String?.shouldContainWithDiff(expected: String) {
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

fun String.stripCodegenPrefix() =
    this.substring(this.indexOf("package test") + "package test".length).trim()
