/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32cTest {
    @Test
    fun testCrc32c() {
        assertEquals(224353407U, "foobar".encodeToByteArray().crc32c())
    }
}
