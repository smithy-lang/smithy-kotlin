/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.RetryErrorType
import aws.smithy.kotlin.runtime.retries.RetryToken
import aws.smithy.kotlin.runtime.retries.RetryTokenBucket

/**
 * A [RetryTokenBucket] that doesn't actually track tokens. All operations immediately succeed and no blocking occurs.
 */
object NoOpTokenBucket : RetryTokenBucket {
    override suspend fun acquireToken(): RetryToken = object : RetryToken {
        override suspend fun notifyFailure() = Unit
        override suspend fun notifySuccess() = Unit
        override suspend fun scheduleRetry(reason: RetryErrorType): RetryToken = this
    }
}
