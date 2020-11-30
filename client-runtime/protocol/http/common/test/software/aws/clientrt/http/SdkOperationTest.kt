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
        val op = SdkOperation.build {
            service = "test"
            operationName = "operation"
            expectedHttpStatus = 418
        }

        assertEquals("test", op[(SdkClientOption.ServiceName)])
        assertEquals("operation", op[SdkClientOption.OperationName])
        assertEquals(418, op[SdkOperation.ExpectedHttpStatus])
        assertNull(op.getOrNull(SdkOperation.OperationSerializer))
    }

    @Test
    fun testMissingRequiredProperties() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SdkOperation.build {
                service = "test"
            }
        }

        assertTrue(ex.message!!.contains("OperationName is a required property"))
    }
}
