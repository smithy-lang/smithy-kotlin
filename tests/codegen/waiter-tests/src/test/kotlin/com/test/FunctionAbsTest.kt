/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionAbsRequest
import com.test.model.GetFunctionAbsResponse
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionAbsTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionAbsRequest) -> Outcome<GetFunctionAbsResponse>,
        vararg results: GetFunctionAbsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionAbsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testAbsFunctionShortEquals() = successTest(
        WaitersTestClient::waitUntilAbsFunctionShortEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = -1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testAbsFunctionIntegerEquals() = successTest(
        WaitersTestClient::waitUntilAbsFunctionIntegerEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = -1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test fun testAbsFunctionLongEquals() = successTest(
        WaitersTestClient::waitUntilAbsFunctionLongEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = -1L } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test fun testAbsFunctionFloatEquals() = successTest(
        WaitersTestClient::waitUntilAbsFunctionFloatEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = -1f } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = 1f } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = 2f } },
    )

    @Test fun testAbsFunctionDoubleEquals() = successTest(
        WaitersTestClient::waitUntilAbsFunctionDoubleEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = -1.0 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
