/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionEndsWithEqualsRequest
import com.test.model.GetFunctionEndsWithEqualsResponse
import com.test.waiters.waitUntilStringEndsWithEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionEndsWithTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionEndsWithEqualsRequest) -> Outcome<GetFunctionEndsWithEqualsResponse>,
        vararg results: GetFunctionEndsWithEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionEndsWithEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testStringEndsWithEquals() = successTest(
        WaitersTestClient::waitUntilStringEndsWithEquals,
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foo" } },
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foobar" } },
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foobarbaz" } },
    )
}
