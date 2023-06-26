/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
suspend fun <T> TestScope.assertTime(expectedDuration: Duration, block: suspend () -> T): T {
    val (result, actualDuration) = testTimeSource.measureTimedValue { block() }

    assertEquals(expectedDuration, actualDuration) {
        "Actual duration $actualDuration doesn't match expected duration $expectedDuration"
    }

    return result
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestScope.measure(block: suspend () -> T): Pair<Int, T> {
    val start = currentTime
    val result = block()
    val actualMs = currentTime - start

    return actualMs.toInt() to result
}
