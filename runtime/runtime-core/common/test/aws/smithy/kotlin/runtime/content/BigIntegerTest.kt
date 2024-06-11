/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BigIntegerTest {
    @Test
    fun testBigInteger() {
        val reallyBigNumberString = "340282366920938463463374607431768211456" // 128-bit number
        val reallyBigNumber = BigInteger(reallyBigNumberString)
        assertEquals(reallyBigNumberString, reallyBigNumber.toString())
    }

    @Test
    fun testBadBigInteger() {
        assertFails { BigInteger("1234567890foo1234567890") }
    }
}
