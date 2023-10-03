/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionSortEqualsRequest
import com.test.model.GetFunctionSortEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilSortNumberEquals
import com.test.waiters.waitUntilSortStringEquals
import org.junit.jupiter.api.Test

class FunctionSortTest {
    @Test fun testSortNumberEquals() = successTest(
        GetFunctionSortEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilSortNumberEquals,
        GetFunctionSortEqualsResponse {
            lists = EntityLists {
                integers = listOf(4, 1, 3, 2, 0)
            }
        },
    )

    @Test fun testSortStringEquals() = successTest(
        GetFunctionSortEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilSortStringEquals,
        GetFunctionSortEqualsResponse {
            lists = EntityLists {
                strings = listOf("foo", "bar", "baz")
            }
        },
    )
}
