/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType

/**
 * A [RetryTokenBucket] that doesn't actually track tokens, effectively simulating "infinite" token capacity. All
 * operations immediately succeed and no blocking occurs.
 */
public object InfiniteTokenBucket : RetryTokenBucket {
    override suspend fun acquireToken(): RetryToken = object : RetryToken {
        override suspend fun notifyFailure() = Unit
        override suspend fun notifySuccess() = Unit
        override suspend fun scheduleRetry(reason: RetryErrorType): RetryToken = this
    }
}
