/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

@InternalApi
public actual fun <T> runBlockingBridge(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = TODO("Not yet implemented on wasmJs: blocking")
