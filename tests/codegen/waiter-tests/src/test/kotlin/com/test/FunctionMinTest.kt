/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionMinEqualsRequest
import com.test.model.GetFunctionMinEqualsResponse
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionMinTest {
    @Test
    fun testMinFunctionShortListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionShortListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { shorts = listOf(0, 10) } },
        GetFunctionMinEqualsResponse { lists = EntityLists { shorts = listOf(10, 20) } },
    )

    @Test
    fun testMinFunctionIntegerListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionIntegerListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { integers = listOf(0, 10) } },
        GetFunctionMinEqualsResponse { lists = EntityLists { integers = listOf(10, 20) } },
    )

    @Test
    fun testMinFunctionLongListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionLongListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { longs = listOf(0L, 10L) } },
        GetFunctionMinEqualsResponse { lists = EntityLists { longs = listOf(10L, 20L) } },
    )

    @Test
    fun testMinFunctionFloatListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionFloatListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { floats = listOf(0f, 10f) } },
        GetFunctionMinEqualsResponse { lists = EntityLists { floats = listOf(10f, 20f) } },
    )

    @Test
    fun testMinFunctionDoubleListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionDoubleListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { doubles = listOf(0.0, 10.0) } },
        GetFunctionMinEqualsResponse { lists = EntityLists { doubles = listOf(10.0, 20.0) } },
    )

    @Test
    fun testMinFunctionStringListEquals() = successTest(
        GetFunctionMinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinFunctionStringListEquals,
        GetFunctionMinEqualsResponse { lists = EntityLists { strings = listOf("bar", "foo") } },
        GetFunctionMinEqualsResponse { lists = EntityLists { strings = listOf("foo", "fooooo") } },
    )
}
