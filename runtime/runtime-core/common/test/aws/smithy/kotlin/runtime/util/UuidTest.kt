/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UuidTest {
    @Test
    fun `it should generate a valid V4 UUID`() {
        val uuid = Uuid.random()

        uuid.toLongs { msb, lsb ->
            val version = (msb ushr 12) and 0xF
            assertTrue(version == 4L, "Expected UUID v4, got version $version")

            val variant = (lsb ushr 62) and 0x3
            assertTrue(variant == 2L, "Expected variant 2 (RFC 9562), got variant $variant")
        }
    }
}
