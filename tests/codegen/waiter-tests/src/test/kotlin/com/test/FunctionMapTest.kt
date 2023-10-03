/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.GetFunctionMapEqualsRequest
import com.test.model.GetFunctionMapEqualsResponse
import com.test.model.Struct
import com.test.utils.successTest
import com.test.waiters.waitUntilMapStructEquals
import org.junit.jupiter.api.Test

class FunctionMapTest {
    @Test fun testMapStructEquals() = successTest(
        GetFunctionMapEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilMapStructEquals,
        GetFunctionMapEqualsResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        string = "foo"
                    },
                    Struct {
                        string = "foo"
                    },
                )
            }
        },
    )
}
