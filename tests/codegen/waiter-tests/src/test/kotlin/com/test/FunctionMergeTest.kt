/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsEquals
import com.test.waiters.waitUntilMergeFunctionPrimitivesAndListsEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionMergeTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionMergeEqualsRequest) -> Outcome<GetFunctionMergeEqualsResponse>,
        vararg results: GetFunctionMergeEqualsResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionMergeEqualsRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testMergeFunctionPrimitivesAndListsEquals() = successTest(
        WaitersTestClient::waitUntilMergeFunctionPrimitivesAndListsEquals,
        GetFunctionMergeEqualsResponse {
            lists = EntityLists { }
            primitives = EntityPrimitives { string = "baz" }
        },
        GetFunctionMergeEqualsResponse {
            lists = EntityLists { }
            primitives = EntityPrimitives { string = "foo" }
        },
    )

    @Test
    fun testMergeFunctionOverrideObjectsEquals() = successTest(
        WaitersTestClient::waitUntilMergeFunctionOverrideObjectsEquals,
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "foo"
                valueTwo = "foo"
                valueThree = "foo"
            }
            objectTwo = Values {
                valueOne = "baz"
                valueTwo = "baz"
                valueThree = "baz"
            }
        },
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "baz"
                valueTwo = "baz"
                valueThree = "baz"
            }
            objectTwo = Values {
                valueOne = "foo"
                valueTwo = "foo"
                valueThree = "foo"
            }
        },
    )
}
