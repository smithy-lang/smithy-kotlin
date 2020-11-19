/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import kotlin.test.*

class SdkOperationTest {

    @Test
    fun testBuilder() {
        val op = SdkOperation.build {
            service = "test"
            operationName = "operation"
            expectedHttpStatus = 418
        }

        assertEquals("test", op.get(SdkOperation.ServiceName))
        assertEquals("operation", op.get(SdkOperation.OperationName))
        assertEquals(418, op.get(SdkOperation.ExpectedHttpStatus))
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
