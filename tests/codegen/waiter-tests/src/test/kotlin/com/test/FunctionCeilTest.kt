/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionCeilRequest
import com.test.model.GetFunctionCeilResponse
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionCeilTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionCeilRequest) -> Outcome<GetFunctionCeilResponse>,
        vararg results: GetFunctionCeilResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionCeilRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testCeilFunctionShortEquals() = successTest(
        WaitersTestClient::waitUntilCeilFunctionShortEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testCeilFunctionIntegerEquals() = successTest(
        WaitersTestClient::waitUntilCeilFunctionIntegerEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test fun testCeilFunctionLongEquals() = successTest(
        WaitersTestClient::waitUntilCeilFunctionLongEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test fun testCeilFunctionFloatEquals() = successTest(
        WaitersTestClient::waitUntilCeilFunctionFloatEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 0.0001f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 0.9f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 1.0f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 2.0f } },
    )

    @Test fun testCeilFunctionDoubleEquals() = successTest(
        WaitersTestClient::waitUntilCeilFunctionDoubleEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 0.0001 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 0.9 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
