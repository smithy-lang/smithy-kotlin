/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionNotNullEqualsRequest
import com.test.model.GetFunctionNotNullEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilNotNullFunctionStringEquals
import kotlin.test.Test

class FunctionNotNullTest {
    @Test
    fun testNotNullFunctionStringEquals() = successTest(
        GetFunctionNotNullEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilNotNullFunctionStringEquals,
        GetFunctionNotNullEqualsResponse {
            primitives = EntityPrimitives {
                string = "foo"
                integer = 10
            }
            lists = EntityLists {
                strings = listOf("baz")
            }
        },
        GetFunctionNotNullEqualsResponse {
            primitives = EntityPrimitives {
                string = "foo"
                integer = 10
            }
            lists = EntityLists { }
        },
    )
}
