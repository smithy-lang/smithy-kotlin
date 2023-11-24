/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.model.Enum
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionContainsTest {
    // list
    @Test
    fun testBooleanListContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, false) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, true) }
        },
    )

    @Test
    fun testBooleanListContainsIdentityProjection() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListContainsIdentityProjection,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, false) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, true) }
        },
    )

    @Test
    fun testStringListContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilStringListContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { string = "bar" }
            lists = EntityLists { strings = listOf("foo", "baz") }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { string = "bar" }
            lists = EntityLists { strings = listOf("foo", "bar") }
        },
    )

    @Test
    fun testIntegerListContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerListContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { integer = 10 }
            lists = EntityLists { integers = listOf(8, 9) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { integer = 10 }
            lists = EntityLists { integers = listOf(9, 10) }
        },
    )

    @Test
    fun testEnumListContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilEnumListContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            lists = EntityLists { enums = listOf(Enum.One, Enum.One) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            lists = EntityLists { enums = listOf(Enum.One, Enum.Two) }
        },
    )

    @Test
    fun testIntEnumListContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilIntEnumListContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.One) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.Two) }
        },
    )

    // object projection
    @Test
    fun testBooleanMapContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanMapContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = false }
            maps = EntityMaps { booleans = mapOf("i" to true, "j" to true) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { boolean = false }
            maps = EntityMaps { booleans = mapOf("i" to true, "j" to false) }
        },
    )

    @Test
    fun testStringMapContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilStringMapContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { string = "bar" }
            maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "baz") }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { string = "bar" }
            maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "bar") }
        },
    )

    @Test
    fun testIntegerMapContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerMapContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { integer = 10 }
            maps = EntityMaps { integers = mapOf("i" to 9, "j" to 11) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { integer = 10 }
            maps = EntityMaps { integers = mapOf("i" to 9, "j" to 10) }
        },
    )

    @Test
    fun testEnumMapContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilEnumMapContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.One) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.Two) }
        },
    )

    @Test
    fun testIntEnumMapContains() = successTest(
        GetFunctionContainsRequest { name = "test" },
        WaitersTestClient::waitUntilIntEnumMapContains,
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.One) }
        },
        GetFunctionContainsResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.Two) }
        },
    )
}
