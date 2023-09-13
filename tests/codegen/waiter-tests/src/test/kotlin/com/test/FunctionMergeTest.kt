/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.GetFunctionMergeEqualsRequest
import com.test.model.GetFunctionMergeEqualsResponse
import com.test.model.Values
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsOneEquals
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsThreeEquals
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsTwoEquals
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
    fun testMergeFunctionOverrideObjectsOneEquals() = successTest(
        WaitersTestClient::waitUntilMergeFunctionOverrideObjectsOneEquals,
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
            objectTwo = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
        },
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
            objectTwo = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
        },
    )

    @Test
    fun testMergeFunctionOverrideObjectsTwoEquals() = successTest(
        WaitersTestClient::waitUntilMergeFunctionOverrideObjectsTwoEquals,
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
            objectTwo = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
        },
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
            objectTwo = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
        },
    )

    @Test
    fun testMergeFunctionOverrideObjectsThreeEquals() = successTest(
        WaitersTestClient::waitUntilMergeFunctionOverrideObjectsThreeEquals,
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
            objectTwo = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
        },
        GetFunctionMergeEqualsResponse {
            objectOne = Values {
                valueOne = "qux"
                valueTwo = "qux"
                valueThree = "qux"
            }
            objectTwo = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
        },
    )
}
