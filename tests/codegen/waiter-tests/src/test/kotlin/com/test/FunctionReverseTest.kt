/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetFunctionReverseEqualsRequest
import com.test.model.GetFunctionReverseEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilReverseFunctionStringEquals
import com.test.waiters.waitUntilReverseFunctionStringListEquals
import org.junit.jupiter.api.Test

class FunctionReverseTest {
    @Test fun testReverseFunctionStringListEquals() = successTest(
        GetFunctionReverseEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilReverseFunctionStringListEquals,
        GetFunctionReverseEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
        GetFunctionReverseEqualsResponse { lists = EntityLists { strings = listOf("bar", "foo") } },
    )

    @Test fun testReverseFunctionStringEquals() = successTest(
        GetFunctionReverseEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilReverseFunctionStringEquals,
        GetFunctionReverseEqualsResponse { primitives = EntityPrimitives { string = "foo" } },
        GetFunctionReverseEqualsResponse { primitives = EntityPrimitives { string = "oof" } },
    )
}
