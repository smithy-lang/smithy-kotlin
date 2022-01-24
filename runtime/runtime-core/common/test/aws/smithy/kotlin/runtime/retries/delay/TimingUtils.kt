/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.delay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.assertTrue

private const val timeToleranceMs = 20

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestCoroutineScope.assertTime(expectedMs: Int, block: suspend () -> T): T {
    val (actualMs, result) = measure(block)

    val expectedRangeMs = (expectedMs - timeToleranceMs)..(expectedMs + timeToleranceMs)
    assertTrue(actualMs in expectedRangeMs, "Actual ms $actualMs isn't in expected range $expectedRangeMs")

    return result
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestCoroutineScope.measure(block: suspend () -> T): Pair<Int, T> {
    val start = currentTime
    val result = block()
    val actualMs = currentTime - start

    return actualMs.toInt() to result
}
