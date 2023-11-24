/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionAbsRequest
import com.test.model.GetFunctionAbsResponse
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionAbsTest {
    @Test
    fun testAbsFunctionShortEquals() = successTest(
        GetFunctionAbsRequest { name = "test" },
        WaitersTestClient::waitUntilAbsFunctionShortEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = -1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test
    fun testAbsFunctionIntegerEquals() = successTest(
        GetFunctionAbsRequest { name = "test" },
        WaitersTestClient::waitUntilAbsFunctionIntegerEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = -1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test
    fun testAbsFunctionLongEquals() = successTest(
        GetFunctionAbsRequest { name = "test" },
        WaitersTestClient::waitUntilAbsFunctionLongEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = -1L } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test
    fun testAbsFunctionFloatEquals() = successTest(
        GetFunctionAbsRequest { name = "test" },
        WaitersTestClient::waitUntilAbsFunctionFloatEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = -1f } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = 1f } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { float = 2f } },
    )

    @Test
    fun testAbsFunctionDoubleEquals() = successTest(
        GetFunctionAbsRequest { name = "test" },
        WaitersTestClient::waitUntilAbsFunctionDoubleEquals,
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = -1.0 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionAbsResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
