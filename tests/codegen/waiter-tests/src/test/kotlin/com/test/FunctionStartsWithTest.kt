/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionStartsWithEqualsRequest
import com.test.model.GetFunctionStartsWithEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilStringStartsWithEquals
import org.junit.jupiter.api.Test

class FunctionStartsWithTest {
    @Test fun testStringStartsWithEquals() = successTest(
        GetFunctionStartsWithEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringStartsWithEquals,
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "baz" } },
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "barbaz" } },
        GetFunctionStartsWithEqualsResponse { primitives = EntityPrimitives { string = "foobarbaz" } },
    )
}
