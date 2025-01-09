/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Internal coroutine dispatchers used by the SDK
 */
@InternalApi
public expect object SdkDispatchers {
    /**
     * The CoroutineDispatcher that is designed for offloading blocking IO tasks to a shared pool of threads.
     * On JVM this is guaranteed to be `Dispatchers.IO`
     */
    public val IO: CoroutineDispatcher
}
