/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionToNumberEqualsRequest
import com.test.model.GetFunctionToNumberEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilToNumberFunctionIntegerEquals
import com.test.waiters.waitUntilToNumberFunctionStringEquals
import kotlin.test.Test

class FunctionToNumberTest {
    @Test
    fun testToNumberFunctionStringEquals() = successTest(
        GetFunctionToNumberEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToNumberFunctionStringEquals,
        GetFunctionToNumberEqualsResponse {
            primitives = EntityPrimitives { string = "20" }
        },
        GetFunctionToNumberEqualsResponse {
            primitives = EntityPrimitives { string = "10" }
        },
    )

    @Test
    fun testToNumberFunctionIntegerEquals() = successTest(
        GetFunctionToNumberEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToNumberFunctionIntegerEquals,
        GetFunctionToNumberEqualsResponse {
            primitives = EntityPrimitives { integer = 20 }
        },
        GetFunctionToNumberEqualsResponse {
            primitives = EntityPrimitives { integer = 10 }
        },
    )
}
