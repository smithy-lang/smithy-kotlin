/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionToStringEqualsRequest
import com.test.model.GetFunctionToStringEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilToStringFunctionBooleanEquals
import com.test.waiters.waitUntilToStringFunctionStringEquals
import kotlin.test.Test

class FunctionToStringTest {
    @Test
    fun testToStringFunctionStringEquals() = successTest(
        GetFunctionToStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToStringFunctionStringEquals,
        GetFunctionToStringEqualsResponse {
            primitives = EntityPrimitives { string = "baz" }
        },
        GetFunctionToStringEqualsResponse {
            primitives = EntityPrimitives { string = "foo" }
        },
    )

    @Test
    fun testToStringFunctionBooleanEquals() = successTest(
        GetFunctionToStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToStringFunctionBooleanEquals,
        GetFunctionToStringEqualsResponse {
            primitives = EntityPrimitives { boolean = false }
        },
        GetFunctionToStringEqualsResponse {
            primitives = EntityPrimitives { boolean = true }
        },
    )
}
