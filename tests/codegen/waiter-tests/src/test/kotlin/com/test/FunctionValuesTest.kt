/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import com.test.model.GetFunctionValuesEqualsRequest
import com.test.model.GetFunctionValuesEqualsResponse
import com.test.model.Values
import com.test.utils.successTest
import com.test.waiters.waitUntilValuesFunctionAnySampleValuesEquals
import com.test.waiters.waitUntilValuesFunctionSampleValuesEquals
import kotlin.test.Test

class FunctionValuesTest {
    @Test
    fun testValuesFunctionSampleValuesEquals() = successTest(
        GetFunctionValuesEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilValuesFunctionSampleValuesEquals,
        GetFunctionValuesEqualsResponse {
            sampleValues = Values {
                valueOne = "baz"
                valueTwo = "baz"
                valueThree = "baz"
            }
        },
        GetFunctionValuesEqualsResponse {
            sampleValues = Values {
                valueOne = "foo"
                valueTwo = "foo"
                valueThree = "foo"
            }
        },
    )

    @Test
    fun testValuesFunctionAnySampleValuesEquals() = successTest(
        GetFunctionValuesEqualsRequest { name = "test" },
        WaitersTestClient::waitUntilValuesFunctionAnySampleValuesEquals,
        GetFunctionValuesEqualsResponse {
            sampleValues = Values {
                valueOne = "bar"
                valueTwo = "baz"
                valueThree = "qux"
            }
        },
        GetFunctionValuesEqualsResponse {
            sampleValues = Values {
                valueOne = "foo"
                valueTwo = "bar"
                valueThree = "baz"
            }
        },
    )
}
