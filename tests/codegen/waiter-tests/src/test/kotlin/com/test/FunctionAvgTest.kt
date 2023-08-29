/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityLists
import com.test.model.GetFunctionAvgEqualsRequest
import com.test.model.GetFunctionAvgEqualsResponse
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionAvgTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionAvgEqualsRequest) -> Outcome<GetFunctionAvgEqualsResponse>,
        vararg results: GetFunctionAvgEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionAvgEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testShortListAvgEquals() = successTest(
        WaitersTestClient::waitUntilShortListAvgEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(12, 12, 10, 8, 8) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(10, 10) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(0) } },
    )

    @Test
    fun testIntegerListAvgEquals() = successTest(
        WaitersTestClient::waitUntilIntegerListAvgEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(12, 12, 10, 8, 8) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(10, 10) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(0) } },
    )

    @Test
    fun testLongListAvgEquals() = successTest(
        WaitersTestClient::waitUntilLongListAvgEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(12L, 12L, 10L, 8L, 8L) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(10L, 10L) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(0L) } },
    )

    @Test
    fun testFloatListAvgEquals() = successTest(
        WaitersTestClient::waitUntilFloatListAvgEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(12f, 12f, 10f, 8f, 8f) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(10f, 10f) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(0f) } },
    )

    @Test
    fun testDoubleListAvgEquals() = successTest(
        WaitersTestClient::waitUntilDoubleListAvgEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(12.0, 12.0, 10.0, 8.0, 8.0) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(10.0, 10.0) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(0.0) } },
    )
}
