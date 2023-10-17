/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.EntityPrimitives
import com.test.model.GetFunctionKeysEqualsRequest
import com.test.model.GetFunctionKeysEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilKeysFunctionPrimitivesIntegerEquals
import com.test.waiters.waitUntilKeysFunctionPrimitivesStringEquals
import kotlin.test.Test

class FunctionKeysTest {
    @Test
    fun testKeysFunctionPrimitivesStringEquals() = successTest(
        GetFunctionKeysEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilKeysFunctionPrimitivesStringEquals,
        GetFunctionKeysEqualsResponse { primitives = EntityPrimitives { } },
    )

    @Test
    fun testKeysFunctionPrimitivesIntegerEquals() = successTest(
        GetFunctionKeysEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilKeysFunctionPrimitivesIntegerEquals,
        GetFunctionKeysEqualsResponse { primitives = EntityPrimitives { } },
    )
}
