/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionJoinEqualsRequest
import com.test.model.GetFunctionJoinEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilStringListJoinEquals
import com.test.waiters.waitUntilStringListSeparatorJoinEquals
import kotlin.test.Test

class FunctionJoinTest {
    @Test
    fun testStringListJoinEquals() = successTest(
        GetFunctionJoinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringListJoinEquals,
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("f", "o", "x") } },
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("f", "o", "o") } },
    )

    @Test
    fun testStringListSeparatorJoinEquals() = successTest(
        GetFunctionJoinEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringListSeparatorJoinEquals,
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
        GetFunctionJoinEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
    )
}
