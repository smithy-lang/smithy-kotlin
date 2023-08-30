/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityLists
import com.test.model.GetFunctionJoinEqualsRequest
import com.test.model.GetFunctionJoinEqualsResponse
import com.test.waiters.waitUntilStringListJoinEquals
import com.test.waiters.waitUntilStringListSeparatorJoinEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionJoinTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionJoinEqualsRequest) -> Outcome<GetFunctionJoinEqualsResponse>,
        vararg results: GetFunctionJoinEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionJoinEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testStringListJoinEquals() = successTest(
        WaitersTestClient::waitUntilStringListJoinEquals,
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("f", "o", "x") } },
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("f", "o", "o") } },
    )

    @Test
    fun testStringListSeparatorJoinEquals() = successTest(
        WaitersTestClient::waitUntilStringListSeparatorJoinEquals,
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
    )
}
