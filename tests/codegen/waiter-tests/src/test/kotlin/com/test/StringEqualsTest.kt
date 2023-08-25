/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.EntityLists
import com.test.model.Enum
import com.test.model.GetStringEqualsRequest
import com.test.model.GetStringEqualsResponse
import com.test.waiters.waitUntilEnumListAllStringEquals
import com.test.waiters.waitUntilEnumListAnyStringEquals
import com.test.waiters.waitUntilStringListAllStringEquals
import com.test.waiters.waitUntilStringListAnyStringEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StringEqualsTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetStringEqualsRequest) -> Outcome<GetStringEqualsResponse>,
        vararg results: GetStringEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetStringEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // anyStringEquals
    @Test fun testStringListAnyListStringEquals() = successTest(
        WaitersTestClient::waitUntilStringListAnyStringEquals,
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
    )

    @Test fun testEnumListAnyStringEquals() = successTest(
        WaitersTestClient::waitUntilEnumListAnyStringEquals,
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.Two) } },
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.One) } },
    )

    // allStringEquals
    @Test fun testStringListAllStringEquals() = successTest(
        WaitersTestClient::waitUntilStringListAllStringEquals,
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo", "foo") } },
    )

    @Test fun testEnumListAllStringEquals() = successTest(
        WaitersTestClient::waitUntilEnumListAllStringEquals,
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.Two) } },
        GetStringEqualsResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.One) } },
    )
}
