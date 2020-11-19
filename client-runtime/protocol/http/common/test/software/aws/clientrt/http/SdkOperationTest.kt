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

        assertEquals("test", op.attribute(SdkOperation.ServiceName))
        assertEquals("operation", op.attribute(SdkOperation.OperationName))
        assertEquals(418, op.attribute(SdkOperation.ExpectedHttpStatus))
        assertNull(op.attributeOrNull(SdkOperation.OperationSerializer))
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
