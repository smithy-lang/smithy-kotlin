/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.coroutines.CoroutineContext

actual fun <T> runSuspendTest(context: CoroutineContext, block: suspend CoroutineScope.() -> T): dynamic =
    GlobalScope.promise(context) { block() }
