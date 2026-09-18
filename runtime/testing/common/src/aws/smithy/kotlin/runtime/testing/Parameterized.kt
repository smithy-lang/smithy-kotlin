/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import kotlin.test.fail

/**
 * Runs [test] for each element in [cases], collecting all failures and reporting them at the end.
 * This is a cross-platform alternative to JUnit's `@ParameterizedTest`.
 *
 * Example (single parameter):
 * ```
 * @Test
 * fun testPrimitives() = parameterized(listOf("String", "Boolean", "Byte")) { type ->
 *     val symbol = provider.toSymbol(model.expectShape("smithy.api#$type"))
 *     assertEquals("kotlin", symbol.namespace)
 * }
 * ```
 *
 * Example (multiple parameters using [Pair]):
 * ```
 * @Test
 * fun testTimestamps() = parameterized(
 *     listOf(
 *         "EPOCH_SECONDS" to "epoch-seconds",
 *         "ISO_8601" to "date-time",
 *     ),
 * ) { (runtimeEnum, formatTrait) ->
 *     // ...
 * }
 * ```
 *
 * Example (multiple parameters using [Triple]):
 * ```
 * @Test
 * fun testDefaults() = parameterized(
 *     listOf(
 *         Triple("String", "\"foo\"", "\"foo\""),
 *         Triple("Boolean", "false", "false"),
 *     ),
 * ) { (type, modelDefault, expectedDefault) ->
 *     // ...
 * }
 * ```
 */
public fun <T> parameterized(cases: List<T>, test: (T) -> Unit) {
    val failures = cases.mapNotNull { case ->
        runCatching { test(case) }.exceptionOrNull()?.let { case to it }
    }
    if (failures.isNotEmpty()) {
        fail(
            buildString {
                appendLine("${failures.size}/${cases.size} case(s) failed:")
                failures.forEach { (case, error) ->
                    appendLine("  [$case]: ${error.message}")
                }
            },
        )
    }
}
