/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test.utils

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.DefaultWaitersTestClient
import com.test.WaitersTestClient
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

internal fun <Request, Response> successTest(
    request: Request,
    waiter: suspend WaitersTestClient.(request: Request) -> Outcome<Response>,
    vararg results: Response,
): Unit = runTest {
    val client = DefaultWaitersTestClient(results.map { Result.success(it) })

    val outcome = client.waiter(request)
    assertEquals(results.size, outcome.attempts)
    assertEquals(results.last(), outcome.getOrThrow())
}
