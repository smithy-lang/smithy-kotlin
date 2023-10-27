/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal actual fun runBlockingTest(
    context: CoroutineContext,
    timeout: Duration?,
    block: suspend CoroutineScope.() -> Unit,
) {
    runBlocking(context) {
        if (timeout != null) {
            withTimeout(timeout) {
                block()
            }
        } else {
            block()
        }
    }
}
