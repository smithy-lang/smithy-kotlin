/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs [block] and blocks the calling thread until it completes, for synchronous APIs that must call suspending code.
 * Only JVM and Kotlin/Native can block; on wasmJs this is not implemented.
 */
@InternalApi
public expect fun <T> runBlockingBridge(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T
