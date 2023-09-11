/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.EntityLists
import com.test.model.Enum
import com.test.model.GetStringEqualsRequest
import com.test.model.GetStringEqualsResponse
import com.test.utils.successTest
import com.test.waiters.waitUntilEnumListAllStringEquals
import com.test.waiters.waitUntilEnumListAnyStringEquals
import com.test.waiters.waitUntilStringListAllStringEquals
import com.test.waiters.waitUntilStringListAnyStringEquals
import org.junit.jupiter.api.Test

class StringEqualsTest {
    // anyStringEquals
    @Test fun testStringListAnyListStringEquals() = successTest(
        GetStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringListAnyStringEquals,
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
    )

    @Test fun testEnumListAnyStringEquals() = successTest(
        GetStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilEnumListAnyStringEquals,
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.Two) } },
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.One) } },
    )

    // allStringEquals
    @Test fun testStringListAllStringEquals() = successTest(
        GetStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilStringListAllStringEquals,
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "foo") } },
    )

    @Test fun testEnumListAllStringEquals() = successTest(
        GetStringEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilEnumListAllStringEquals,
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.Two) } },
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.One) } },
    )
}
