/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionAvgEqualsRequest
import com.test.model.GetFunctionAvgEqualsResponse
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class FunctionAvgTest {
    @Test fun testEmptyIntegerListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilEmptyIntegerListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf() } },
    )

    @Test fun testShortListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilShortListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(12, 12, 10, 8, 8) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(10, 10) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { shorts = listOf(0) } },
    )

    @Test fun testIntegerListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(12, 12, 10, 8, 8) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(10, 10) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { integers = listOf(0) } },
    )

    @Test fun testLongListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilLongListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(12L, 12L, 10L, 8L, 8L) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(10L, 10L) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { longs = listOf(0L) } },
    )

    @Test fun testFloatListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilFloatListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(12f, 12f, 10f, 8f, 8f) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(10f, 10f) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { floats = listOf(0f) } },
    )

    @Test fun testDoubleListAvgNotEquals() = successTest(
        GetFunctionAvgEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilDoubleListAvgNotEquals,
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(12.0, 12.0, 10.0, 8.0, 8.0) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(10.0, 10.0) } },
        GetFunctionAvgEqualsResponse { lists = EntityLists { doubles = listOf(0.0) } },
    )
}
