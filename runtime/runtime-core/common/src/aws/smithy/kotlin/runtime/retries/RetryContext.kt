/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * A coroutine context element that carries per-call retry state. Installed by the retry middleware
 * so that the retry strategy can read the server-specified retry-after value without mutable state
 * on the shared strategy instance.
 */
@InternalApi
public class RetryContext : AbstractCoroutineContextElement(Key) {
    /**
     * The server-specified retry-after duration from the `x-amz-retry-after` response header.
     * Written by the retry middleware after each attempt, read by the delay provider before computing backoff.
     */
    public var retryAfter: Duration? = null

    public companion object Key : CoroutineContext.Key<RetryContext>
}
