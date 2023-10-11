/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionMinByEqualsRequest
import com.test.model.GetFunctionMinByEqualsResponse
import com.test.model.Struct
import com.test.utils.successTest
import com.test.waiters.waitUntilMinByNumberEquals
import com.test.waiters.waitUntilMinByStringEquals
import org.junit.jupiter.api.Test

class FunctionMinByTest {
    @Test fun testMinByNumberEquals() = successTest(
        GetFunctionMinByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinByNumberEquals,
        GetFunctionMinByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        integer = 100
                    },
                    Struct {
                        integer = 200
                    },
                )
            }
        },
    )

    @Test fun testMinByStringEquals() = successTest(
        GetFunctionMinByEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMinByStringEquals,
        GetFunctionMinByEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        string = "foo"
                    },
                    Struct {
                        string = "qux"
                    },
                )
            }
        },
    )
}
