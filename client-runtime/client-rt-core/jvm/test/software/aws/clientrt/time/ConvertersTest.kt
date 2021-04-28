/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

import kotlin.test.*

class ConvertersTest {
    @Test
    fun convertInstantFromJvmToAwsSdk() {
        val jvmInstant = java.time.Instant.now()!!
        val awsSdkInstant = jvmInstant.toSdkInstant()
        assertEquals(jvmInstant.epochSecond, awsSdkInstant.epochSeconds)
    }

    @Test
    fun convertInstantFromAwsSdkToJvm() {
        val awsSdkInstant = software.aws.clientrt.time.Instant.now()
        val jvmInstant = awsSdkInstant.toJvmInstant()
        assertEquals(awsSdkInstant.epochSeconds, jvmInstant.epochSecond)
    }
}
