/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionToArrayEqualsRequest
import com.test.model.GetFunctionToArrayEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilToArrayFunctionBooleanEquals
import com.test.waiters.waitUntilToArrayFunctionStringListEquals
import kotlin.test.Test

class FunctionToArrayTest {
    @Test
    fun testToArrayFunctionStringListEquals() = successTest(
        GetFunctionToArrayEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToArrayFunctionStringListEquals,
        GetFunctionToArrayEqualsResponse {
            lists = EntityLists { strings = listOf("foo", "baz") }
        },
        GetFunctionToArrayEqualsResponse {
            lists = EntityLists { strings = listOf("foo", "foo") }
        },
    )

    @Test
    fun testToArrayFunctionBooleanEquals() = successTest(
        GetFunctionToArrayEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilToArrayFunctionBooleanEquals,
        GetFunctionToArrayEqualsResponse {
            primitives = EntityPrimitives { boolean = false }
        },
        GetFunctionToArrayEqualsResponse {
            primitives = EntityPrimitives { boolean = true }
        },
    )
}
