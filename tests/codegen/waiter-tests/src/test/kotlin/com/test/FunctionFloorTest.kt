/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionFloorRequest
import com.test.model.GetFunctionFloorResponse
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionFloorTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionFloorRequest) -> Outcome<GetFunctionFloorResponse>,
        vararg results: GetFunctionFloorResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionFloorRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testFloorFunctionShortEquals() = successTest(
        WaitersTestClient::waitUntilFloorFunctionShortEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testFloorFunctionIntegerEquals() = successTest(
        WaitersTestClient::waitUntilFloorFunctionIntegerEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test fun testFloorFunctionLongEquals() = successTest(
        WaitersTestClient::waitUntilFloorFunctionLongEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test fun testFloorFunctionFloatEquals() = successTest(
        WaitersTestClient::waitUntilFloorFunctionFloatEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.0001f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.9f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.0f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 2.0f } },
    )

    @Test fun testFloorFunctionDoubleEquals() = successTest(
        WaitersTestClient::waitUntilFloorFunctionDoubleEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.0001 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.9 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
