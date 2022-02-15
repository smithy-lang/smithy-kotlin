/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.engine.ktor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class WaiterTest {
    @Test
    fun testSignalWhenWaiting() = runBlockingTest {
        val start = currentTime

        val waiter = Waiter()
        launch {
            delay(500.milliseconds)
            waiter.signal()
        }
        waiter.wait()

        assertEquals(500, currentTime - start)
    }

    @Test
    fun testSignalWhenNotWaiting() = runBlockingTest {
        val start = currentTime

        val waiter = Waiter()
        launch {
            delay(500.milliseconds)
            waiter.signal()
        }
        delay(1000.milliseconds)

        assertEquals(1000, currentTime - start)
    }
}
