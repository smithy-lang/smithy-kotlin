/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionMaxByEqualsRequest
import com.test.model.GetFunctionMaxByEqualsResponse
import com.test.model.Struct
import com.test.utils.successTest
import com.test.waiters.waitUntilMaxByNumberEquals
import com.test.waiters.waitUntilMaxByStringEquals
import org.junit.jupiter.api.Test

class FunctionMaxByTest {
    @Test fun testMaxByNumberEquals() = successTest(
        GetFunctionMaxByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxByNumberEquals,
        GetFunctionMaxByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        integer = 0
                    },
                    Struct {
                        integer = 100
                    },
                )
            }
        },
    )

    @Test fun testMaxByStringEquals() = successTest(
        GetFunctionMaxByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMaxByStringEquals,
        GetFunctionMaxByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        string = "foo"
                    },
                    Struct {
                        string = "bar"
                    },
                )
            }
        },
    )
}
