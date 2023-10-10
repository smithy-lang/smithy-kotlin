/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionSortByEqualsRequest
import com.test.model.GetFunctionSortByEqualsResponse
import com.test.model.Struct
import com.test.utils.successTest
import com.test.waiters.waitUntilSortByNumberEquals
import com.test.waiters.waitUntilSortByStringEquals
import org.junit.jupiter.api.Test

class FunctionSortByTest {
    @Test fun testSortedByNumberEquals() = successTest(
        GetFunctionSortByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilSortByNumberEquals,
        GetFunctionSortByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        integer = 3
                    },
                    Struct {
                        integer = 2
                    },
                    Struct {
                        integer = 1
                    },
                )
            }
        },
    )

    @Test fun testSortByStringEquals() = successTest(
        GetFunctionSortByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilSortByStringEquals,
        GetFunctionSortByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        string = "foo"
                    },
                    Struct {
                        string = "bar"
                    },
                    Struct {
                        string = "baz"
                    },
                )
            }
        },
    )
}
