/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.util.get
import io.kotest.matchers.string.shouldContain
import kotlin.test.*

class HttpOperationContextTest {

    @Test
    fun testBuilder() {
        val op = HttpOperationContext.build {
            operationName = "operation"
            serviceName = "service"
        }

        assertEquals("operation", op[SdkClientOption.OperationName])
    }

    @Test
    fun testMissingRequiredProperties() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HttpOperationContext.build {
            }
        }

        ex.message.shouldContain("operationName is a required HTTP execution attribute")
    }
}
