/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionFloorRequest
import com.test.model.GetFunctionFloorResponse
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class FunctionFloorTest {
    @Test fun testFloorFunctionShortEquals() = successTest(
        GetFunctionFloorRequest { name = "test" },
        WaitersTestClient::waitUntilFloorFunctionShortEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testFloorFunctionIntegerEquals() = successTest(
        GetFunctionFloorRequest { name = "test" },
        WaitersTestClient::waitUntilFloorFunctionIntegerEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test fun testFloorFunctionLongEquals() = successTest(
        GetFunctionFloorRequest { name = "test" },
        WaitersTestClient::waitUntilFloorFunctionLongEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test fun testFloorFunctionFloatEquals() = successTest(
        GetFunctionFloorRequest { name = "test" },
        WaitersTestClient::waitUntilFloorFunctionFloatEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.0001f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.9f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 1.0f } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { float = 2.0f } },
    )

    @Test fun testFloorFunctionDoubleEquals() = successTest(
        GetFunctionFloorRequest { name = "test" },
        WaitersTestClient::waitUntilFloorFunctionDoubleEquals,
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.0001 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.9 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionFloorResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
