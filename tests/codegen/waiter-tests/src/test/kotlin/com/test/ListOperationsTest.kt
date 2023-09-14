/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class ListOperationsTest {
    // list indexing
    @Test fun testBooleanListIndexZeroEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListIndexZeroEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true) } },
    )

    @Test fun testBooleanListIndexOneEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListIndexOneEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, true) } },
    )

    @Test fun testBooleanListIndexNegativeTwoEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListIndexNegativeTwoEquals,
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetListOperationResponse { lists = EntityLists { booleans = listOf(true, false) } },
    )

    @Test fun testTwoDimensionalBooleanListIndexZeroZeroEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilTwoDimensionalBooleanListIndexZeroZeroEquals,
        GetListOperationResponse { twoDimensionalLists = TwoDimensionalEntityLists { booleansList = listOf(listOf(false)) } },
        GetListOperationResponse { twoDimensionalLists = TwoDimensionalEntityLists { booleansList = listOf(listOf(true)) } },
    )

    @Test fun testStructListIndexOneStringsIndexZeroEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStructListIndexOneStringsIndexZeroEquals,
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { strings = listOf("bar") }, Struct { strings = listOf("bar") }) } },
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { strings = listOf("bar") }, Struct { strings = listOf("foo") }) } },
    )

    @Test fun testStructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals,
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { }, Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { boolean = false } }) }) } },
        GetListOperationResponse { lists = EntityLists { structs = listOf(Struct { }, Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { boolean = true } }) }) } },
    )

    // list slicing
    @Test fun testStringListStepSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "bar") } },
    )

    @Test fun testStringListStopSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStartSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStartSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "bar") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "foo") } },
    )

    @Test fun testStringListStopStepSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStopStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "foo", "foo", "foo", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "bar", "bar", "bar", "bar", "bar") } },
    )

    @Test fun testStringListStartStepSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStartStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo", "foo", "bar") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar", "bar", "foo") } },
    )

    @Test fun testStringListStartStopSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStartStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "foo", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "bar", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStartStopStepSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStartStopStepSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar", "foo", "bar") } },
    )

    @Test fun testStringListNegativeStartStopSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListNegativeStartStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "foo", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "bar", "foo", "bar") } },
    )

    @Test fun testStringListStartNegativeStopSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStartNegativeStopSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "foo", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "bar", "bar") } },
    )

    @Test fun testStringListStopNegativeStartSlicingEquals() = successTest(
        GetListOperationRequest { name = "test" },
        WaitersTestClient::waitUntilStringListStopNegativeStartSlicingEquals,
        GetListOperationResponse { lists = EntityLists { strings = listOf("foo", "bar", "bar", "foo") } },
        GetListOperationResponse { lists = EntityLists { strings = listOf("bar", "foo", "foo", "bar") } },
    )
}
