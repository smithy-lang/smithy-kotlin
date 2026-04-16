/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

/**
 * A source of time
 */
public interface Clock {
    /**
     * Get the current time from the clock source
     */
    public fun now(): Instant

    /**
     * A clock based on system time
     */
    public object System : Clock {
        override fun now(): Instant = Instant.now()
    }

    public companion object
}
