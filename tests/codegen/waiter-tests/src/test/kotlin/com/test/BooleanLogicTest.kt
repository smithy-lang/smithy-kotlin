/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.EntityPrimitives
import com.test.model.GetBooleanLogicRequest
import com.test.model.GetBooleanLogicResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilAndEquals
import com.test.waiters.waitUntilNotEquals
import com.test.waiters.waitUntilOrEquals
import org.junit.jupiter.api.Test

class BooleanLogicTest {
    @Test fun testAndEquals() = successTest(
        GetBooleanLogicRequest { name = "test" },
        WaitersTestClient::waitUntilAndEquals,
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, true) } },
    )

    @Test fun testOrEquals() = successTest(
        GetBooleanLogicRequest { name = "test" },
        WaitersTestClient::waitUntilOrEquals,
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(true, false) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, true) } },
        GetBooleanLogicResponse { lists = EntityLists { booleans = listOf(false, false) } },
    )

    @Test fun testNotEquals() = successTest(
        GetBooleanLogicRequest { name = "test" },
        WaitersTestClient::waitUntilNotEquals,
        GetBooleanLogicResponse { primitives = EntityPrimitives { boolean = true } },
        GetBooleanLogicResponse { primitives = EntityPrimitives { boolean = false } },
    )
}
