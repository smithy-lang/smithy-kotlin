/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BigDecimalTest {
    @Test
    fun testBigDecimal() {
        val reallyPreciseNumberString = "0.340282366920938463463374607431768211456" // 128 bits of magnitude
        val reallyPreciseNumber = BigDecimal(reallyPreciseNumberString)
        assertEquals(reallyPreciseNumberString, reallyPreciseNumber.toPlainString())
    }

    @Test
    fun testBadBigDecimal() {
        assertFails { BigDecimal("1234567890.1234567890foo") }
    }
}
