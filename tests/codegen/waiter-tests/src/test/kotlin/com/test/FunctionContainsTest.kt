/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.model.Enum
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionContainsTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionContainsRequest) -> Outcome<GetFunctionContainsResponse>,
        vararg results: GetFunctionContainsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionContainsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // list
    @Test fun testBooleanListContains() = successTest(
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

    @Test fun testBooleanListContainsIdentityProjection() = successTest(
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

    @Test fun testStringListContains() = successTest(
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

    @Test fun testIntegerListContains() = successTest(
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

    @Test fun testEnumListContains() = successTest(
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

    @Test fun testIntEnumListContains() = successTest(
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
    @Test fun testBooleanMapContains() = successTest(
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

    @Test fun testStringMapContains() = successTest(
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

    @Test fun testIntegerMapContains() = successTest(
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

    @Test fun testEnumMapContains() = successTest(
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

    @Test fun testIntEnumMapContains() = successTest(
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
