/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionEndsWithEqualsRequest
import com.test.model.GetFunctionEndsWithEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilStringEndsWithEquals
import kotlin.test.Test

class FunctionEndsWithTest {
    @Test
    fun testStringEndsWithEquals() = successTest(
        GetFunctionEndsWithEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringEndsWithEquals,
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foo" } },
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foobar" } },
        GetFunctionEndsWithEqualsResponse { primitives = EntityPrimitives { string = "foobarbaz" } },
    )
}
