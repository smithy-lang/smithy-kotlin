/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionSumEqualsRequest
import com.test.model.GetFunctionSumEqualsResponse
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionSumTest {
    @Test
    fun testShortListSumNotEquals() = successTest(
        GetFunctionSumEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilShortListSumNotEquals,
        GetFunctionSumEqualsResponse { lists = EntityLists { shorts = listOf(1, 2, 3, 4) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { shorts = listOf(10) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { shorts = listOf(0) } },
    )

    @Test
    fun testIntegerListSumNotEquals() = successTest(
        GetFunctionSumEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerListSumNotEquals,
        GetFunctionSumEqualsResponse { lists = EntityLists { integers = listOf(1, 2, 3, 4) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { integers = listOf(10) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { integers = listOf(0) } },
    )

    @Test
    fun testLongListSumNotEquals() = successTest(
        GetFunctionSumEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilLongListSumNotEquals,
        GetFunctionSumEqualsResponse { lists = EntityLists { longs = listOf(1L, 2L, 3L, 4L) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { longs = listOf(10L) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { longs = listOf(0L) } },
    )

    @Test
    fun testFloatListSumNotEquals() = successTest(
        GetFunctionSumEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilFloatListSumNotEquals,
        GetFunctionSumEqualsResponse { lists = EntityLists { floats = listOf(1f, 2f, 3f, 4f) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { floats = listOf(10f) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { floats = listOf(0f) } },
    )

    @Test
    fun testDoubleListSumNotEquals() = successTest(
        GetFunctionSumEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilDoubleListSumNotEquals,
        GetFunctionSumEqualsResponse { lists = EntityLists { doubles = listOf(1.0, 2.0, 3.0, 4.0) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { doubles = listOf(10.0) } },
        GetFunctionSumEqualsResponse { lists = EntityLists { doubles = listOf(0.0) } },
    )
}
