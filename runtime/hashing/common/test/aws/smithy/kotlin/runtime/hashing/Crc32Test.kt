/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {
    @Test
    fun testCrc32() {
        assertEquals(2666930069U, "foobar".encodeToByteArray().crc32())
    }
}
