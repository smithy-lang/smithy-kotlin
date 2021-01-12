/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.testing

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * MPP compatible runBlocking to run suspend tests in common modules
 */
expect fun runSuspendTest(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit)
