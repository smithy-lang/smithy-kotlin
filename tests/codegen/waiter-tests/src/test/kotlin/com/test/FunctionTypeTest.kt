/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.*
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class FunctionTypeTest {
    @Test fun testTypeFunctionStringEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionStringEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { string = "foo" }
            types = Types { string = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { string = "foo" }
            types = Types { string = "string" }
        },
    )

    @Test fun testTypeFunctionBooleanEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionBooleanEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = true }
            types = Types { boolean = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = true }
            types = Types { boolean = "boolean" }
        },
    )

    @Test fun testTypeFunctionArrayEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionArrayEquals,
        GetFunctionTypeEqualsResponse {
            lists = EntityLists { booleans = listOf(true) }
            types = Types { array = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            lists = EntityLists { booleans = listOf(true) }
            types = Types { array = "array" }
        },
    )

    @Test fun testTypeFunctionShortEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionShortEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { short = 1 }
            types = Types { number = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { short = 1 }
            types = Types { number = "number" }
        },
    )

    @Test fun testTypeFunctionIntegerEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionIntegerEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { integer = 1 }
            types = Types { number = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { integer = 1 }
            types = Types { number = "number" }
        },
    )

    @Test fun testTypeFunctionLongEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionLongEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { long = 1L }
            types = Types { number = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { long = 1L }
            types = Types { number = "number" }
        },
    )

    @Test fun testTypeFunctionFloatEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionFloatEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { float = 1f }
            types = Types { number = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { float = 1f }
            types = Types { number = "number" }
        },
    )

    @Test fun testTypeFunctionDoubleEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionDoubleEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { double = 1.0 }
            types = Types { number = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { double = 1.0 }
            types = Types { number = "number" }
        },
    )

    @Test fun testTypeFunctionObjectEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionObjectEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
            types = Types { objectType = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
            types = Types { objectType = "object" }
        },
    )

    @Test fun testTypeFunctionMergedObjectEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionMergedObjectEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
            types = Types { objectType = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { }
            types = Types { objectType = "object" }
        },
    )

    @Test fun testTypeFunctionNullEquals() = successTest(
        GetFunctionTypeEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilTypeFunctionNullEquals,
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = null }
            types = Types { nullType = "foo" }
        },
        GetFunctionTypeEqualsResponse {
            primitives = EntityPrimitives { boolean = null }
            types = Types { nullType = "null" }
        },
    )
}
