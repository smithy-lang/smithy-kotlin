/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

/**
 * An object that can be used to delay between iterations of code.
 */
interface BackoffDelayer {
    /**
     * Delays for an appropriate amount of time after the given attempt number.
     * @param attempt The ordinal index of the attempt, used in calculating the exact amount of time to delay.
     */
    suspend fun backoff(attempt: Int)
}
