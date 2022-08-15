/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.time.Duration

/**
 * [Clock] implementation that is controlled manually. This is mostly useful for testing purposes.
 * @param epoch When this clock should start from (defaults to current system time)
 */
@InternalApi
public class ManualClock(epoch: Instant = Instant.now()) : Clock {
    private var now: Instant = epoch

    /**
     * Advance the current clock by [duration]
     */
    public fun advance(duration: Duration) {
        now = duration.toComponents { seconds, nanoseconds ->
            Instant.fromEpochSeconds(now.epochSeconds + seconds, now.nanosecondsOfSecond + nanoseconds)
        }
    }

    override fun now(): Instant = now
}
