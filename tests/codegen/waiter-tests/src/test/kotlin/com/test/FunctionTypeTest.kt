/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionTypeEqualsRequest
import com.test.model.GetFunctionTypeEqualsResponse
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionTypeTest {
    @Test
    fun testTypeFunctionStringEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionStringEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { string = "foo" }
        },
    )

    @Test
    fun testTypeFunctionBooleanEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionBooleanEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = true }
        },
    )

    @Test
    fun testTypeFunctionArrayEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionArrayEquals,
        GetFunctionTypeEqualsResponse {
            lists = EntityLists { booleans = listOf(true) }
        },
    )

    @Test
    fun testTypeFunctionShortEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionShortEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { short = 1 }
        },
    )

    @Test
    fun testTypeFunctionIntegerEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionIntegerEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { integer = 1 }
        },
    )

    @Test
    fun testTypeFunctionLongEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionLongEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { long = 1L }
        },
    )

    @Test
    fun testTypeFunctionFloatEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionFloatEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { float = 1f }
        },
    )

    @Test
    fun testTypeFunctionDoubleEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionDoubleEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { double = 1.0 }
        },
    )

    @Test
    fun testTypeFunctionObjectEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionObjectEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
        },
    )

    @Test
    fun testTypeFunctionMergedObjectEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionMergedObjectEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
        },
    )

    @Test
    fun testTypeFunctionNullEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionNullEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = null }
        },
    )
}
