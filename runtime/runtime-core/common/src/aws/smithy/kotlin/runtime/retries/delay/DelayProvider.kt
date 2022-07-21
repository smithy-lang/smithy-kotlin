/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.delay

/**
 * An object that can be used to delay between iterations of code.
 */
public fun interface DelayProvider {
    /**
     * Delays for an appropriate amount of time after the given attempt number.
     * @param attempt The ordinal index of the attempt, used in calculating the exact amount of time to delay.
     */
    public suspend fun backoff(attempt: Int)
}
