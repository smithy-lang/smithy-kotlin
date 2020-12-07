/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.client.SdkClientOption
import software.aws.clientrt.util.get
import kotlin.test.*

class SdkOperationTest {

    @Test
    fun testBuilder() {
        val op = SdkHttpOperation.build {
            service = "test"
            operationName = "operation"
            expectedHttpStatus = 418
        }

        assertEquals("test", op[(SdkClientOption.ServiceName)])
        assertEquals("operation", op[SdkClientOption.OperationName])
        assertEquals(418, op[SdkHttpOperation.ExpectedHttpStatus])
        assertNull(op.getOrNull(SdkHttpOperation.OperationSerializer))
    }

    @Test
    fun testMissingRequiredProperties() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SdkHttpOperation.build {
                service = "test"
            }
        }

        assertTrue(ex.message!!.contains("OperationName is a required property"))
    }
}
