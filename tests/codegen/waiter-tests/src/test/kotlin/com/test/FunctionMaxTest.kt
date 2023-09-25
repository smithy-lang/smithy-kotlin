/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionMaxEqualsRequest
import com.test.model.GetFunctionMaxEqualsResponse
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class FunctionMaxTest {
    @Test fun testMaxFunctionShortListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionShortListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { shorts = listOf(10, 20) } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { shorts = listOf(0, 10) } },
    )

    @Test fun testMaxFunctionIntegerListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionIntegerListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { integers = listOf(10, 20) } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { integers = listOf(0, 10) } },
    )

    @Test fun testMaxFunctionLongListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionLongListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { longs = listOf(10L, 20L) } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { longs = listOf(0L, 10L) } },
    )

    @Test fun testMaxFunctionFloatListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionFloatListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { floats = listOf(10f, 20f) } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { floats = listOf(0f, 10f) } },
    )

    @Test fun testMaxFunctionDoubleListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionDoubleListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { doubles = listOf(10.0, 20.0) } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { doubles = listOf(0.0, 10.0) } },
    )

    @Test fun testMaxFunctionStringListEquals() = successTest(
        GetFunctionMaxEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxFunctionStringListEquals,
        GetFunctionMaxEqualsResponse { lists = EntityLists { strings = listOf("foo", "fooooo") } },
        GetFunctionMaxEqualsResponse { lists = EntityLists { strings = listOf("bar", "foo") } },
    )
}
