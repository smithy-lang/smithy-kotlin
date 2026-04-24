/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * A coroutine context element that carries per-call retry state. Installed by the retry middleware
 * so that the delay provider can read retry metadata without mutable state on the shared strategy instance.
 *
 * Uses a computed property (`get()`) instead of a stored `val` so the value is re-evaluated on each access,
 * allowing tests to toggle the system property between test methods within the same JVM.
 */
@InternalApi
public class RetryContext : AbstractCoroutineContextElement(Key) {
    /**
     * The server-specified retry-after duration from the `x-amz-retry-after` response header.
     */
    public var retryAfter: Duration? = null

    /**
     * The type of error that triggered the retry, set by the strategy before calling backoff.
     */
    public var errorType: RetryErrorType? = null

    public companion object Key : CoroutineContext.Key<RetryContext>
}
