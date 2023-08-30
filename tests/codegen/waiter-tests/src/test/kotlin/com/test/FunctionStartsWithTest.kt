/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionStartsWithEqualsRequest
import com.test.model.GetFunctionStartsWithEqualsResponse
import com.test.waiters.waitUntilStringStartsWithEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionStartsWithTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionStartsWithEqualsRequest) -> Outcome<GetFunctionStartsWithEqualsResponse>,
        vararg results: GetFunctionStartsWithEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionStartsWithEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testStringStartsWithEquals() = successTest(
        WaitersTestClient::waitUntilStringStartsWithEquals,
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "baz" } },
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "barbaz" } },
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "foobarbaz" } },
    )
}
