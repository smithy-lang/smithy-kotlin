/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionCeilRequest
import com.test.model.GetFunctionCeilResponse
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class FunctionCeilTest {
    @Test fun testCeilFunctionShortEquals() = successTest(
        GetFunctionCeilRequest { name = "test" },
        WaitersTestClient::waitUntilCeilFunctionShortEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { short = 1 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testCeilFunctionIntegerEquals() = successTest(
        GetFunctionCeilRequest { name = "test" },
        WaitersTestClient::waitUntilCeilFunctionIntegerEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { integer = 1 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { integer = 2 } },
    )

    @Test fun testCeilFunctionLongEquals() = successTest(
        GetFunctionCeilRequest { name = "test" },
        WaitersTestClient::waitUntilCeilFunctionLongEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { long = 1L } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { long = 2L } },
    )

    @Test fun testCeilFunctionFloatEquals() = successTest(
        GetFunctionCeilRequest { name = "test" },
        WaitersTestClient::waitUntilCeilFunctionFloatEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 0.0001f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 0.9f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 1.0f } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { float = 2.0f } },
    )

    @Test fun testCeilFunctionDoubleEquals() = successTest(
        GetFunctionCeilRequest { name = "test" },
        WaitersTestClient::waitUntilCeilFunctionDoubleEquals,
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 0.0001 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 0.9 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 1.0 } },
        GetFunctionCeilResponse { primitives = EntityPrimitives { double = 2.0 } },
    )
}
