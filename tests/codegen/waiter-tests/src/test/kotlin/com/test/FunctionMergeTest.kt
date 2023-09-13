/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.GetFunctionMergeEqualsRequest
import com.test.model.GetFunctionMergeEqualsResponse
import com.test.model.Values
import com.test.utils.successTest
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsOneEquals
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsThreeEquals
import com.test.waiters.waitUntilMergeFunctionOverrideObjectsTwoEquals
import org.junit.jupiter.api.Test

class FunctionMergeTest {
    @Test fun testMergeFunctionOverrideObjectsOneEquals() = successTest(
        GetFunctionMergeEqualsRequest { name = "test" },
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

    @Test fun testMergeFunctionOverrideObjectsTwoEquals() = successTest(
        GetFunctionMergeEqualsRequest { name = "test" },
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

    @Test fun testMergeFunctionOverrideObjectsThreeEquals() = successTest(
        GetFunctionMergeEqualsRequest { name = "test" },
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
