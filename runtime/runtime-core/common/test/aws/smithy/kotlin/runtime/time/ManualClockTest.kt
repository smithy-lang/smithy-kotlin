/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ManualClockTest {
    @Test
    fun testAdvance() {
        val epoch = 1634413920L
        val clock = ManualClock(epoch = Instant.fromEpochSeconds(epoch))
        assertEquals(epoch, clock.now().epochSeconds)

        clock.advance(20.seconds)
        assertEquals(epoch + 20, clock.now().epochSeconds)

        // negative duration
        clock.advance(-20.seconds)
        assertEquals(epoch, clock.now().epochSeconds)

        // ns
        clock.advance(10_500.milliseconds)
        assertEquals(Instant.fromEpochSeconds(epoch + 10, 500_000_000), clock.now())
    }
}
