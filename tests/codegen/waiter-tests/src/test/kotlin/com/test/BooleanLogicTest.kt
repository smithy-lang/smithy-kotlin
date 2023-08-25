/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetBooleanLogicRequest
import com.test.model.GetBooleanLogicResponse
import com.test.waiters.waitUntilAndEquals
import com.test.waiters.waitUntilNotEquals
import com.test.waiters.waitUntilOrEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BooleanLogicTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetBooleanLogicRequest) -> Outcome<GetBooleanLogicResponse>,
        vararg results: GetBooleanLogicResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetBooleanLogicRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testAndEquals() = successTest(
        WaitersTestClient::waitUntilAndEquals,
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, true) } },
    )

    @Test fun testOrEquals() = successTest(
        WaitersTestClient::waitUntilOrEquals,
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, false) } },
    )

    @Test fun testNotEquals() = successTest(
        WaitersTestClient::waitUntilNotEquals,
        GetBooleanLogicResponse { primitives = EntityPrimitives { boolean = true } },
        GetBooleanLogicResponse { primitives = EntityPrimitives { boolean = false } },
    )
}
