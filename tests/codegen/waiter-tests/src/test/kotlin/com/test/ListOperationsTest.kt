/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ListOperationsTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetListOperationRequest) -> Outcome<GetListOperationResponse>,
        vararg results: GetListOperationResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetListOperationRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // list indexing
    @Test fun testBooleanListIndexZeroEquals() = successTest(
        WaitersTestClient::waitUntilBooleanListIndexZeroEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true) } },
    )

    @Test fun testBooleanListIndexOneEquals() = successTest(
        WaitersTestClient::waitUntilBooleanListIndexOneEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, true) } },
    )

    @Test fun testBooleanListIndexNegativeTwoEquals() = successTest(
        WaitersTestClient::waitUntilBooleanListIndexNegativeTwoEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true, false) } },
    )

    @Test fun testTwoDimensionalBooleanListIndexZeroZeroEquals() = successTest(
        WaitersTestClient::waitUntilTwoDimensionalBooleanListIndexZeroZeroEquals,
        GetListOperationResponse { twoDimensionalLists = TwoDimensionalEntityLists { booleansList = listOf(listOf(false)) } },
        GetListOperationResponse { twoDimensionalLists = TwoDimensionalEntityLists { booleansList = listOf(listOf(true)) } },
    )

    @Test fun testStructListIndexOneStringsIndexZeroEquals() = successTest(
        WaitersTestClient::waitUntilStructListIndexOneStringsIndexZeroEquals,
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { strings = listOf("bar") }, Struct { strings = listOf("bar") }) } },
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { strings = listOf("bar") }, Struct { strings = listOf("foo") }) } },
    )

    @Test fun testStructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals() = successTest(
        WaitersTestClient::waitUntilStructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals,
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { }, Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { boolean = false } }) }) } },
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { }, Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { boolean = true } }) }) } },
    )

    // list slicing
    @Test fun testStringListStepSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "bar") } },
    )

    @Test fun testStringListStopSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStartSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStartSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "bar") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "foo") } },
    )

    @Test fun testStringListStopStepSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStopStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "foo", "foo", "foo", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "bar", "bar", "bar", "bar", "bar") } },
    )

    @Test fun testStringListStartStepSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStartStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo", "foo", "bar") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar", "bar", "foo") } },
    )

    @Test fun testStringListStartStopSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStartStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "foo", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "bar", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStartStopStepSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStartStopStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar", "foo", "bar") } },
    )

    @Test fun testStringListNegativeStartStopSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListNegativeStartStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar") } },
    )

    @Test fun testStringListStartNegativeStopSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStartNegativeStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStopNegativeStartSlicingEquals() = successTest(
        WaitersTestClient::waitUntilStringListStopNegativeStartSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "foo", "bar") } },
    )
}
