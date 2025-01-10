/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.IgnoreNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BigDecimalTest {
    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testBigDecimal() {
        val reallyPreciseNumberString = "0.340282366920938463463374607431768211456" // 128 bits of magnitude
        val reallyPreciseNumber = BigDecimal(reallyPreciseNumberString)
        assertEquals(reallyPreciseNumberString, reallyPreciseNumber.toPlainString())
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testBadBigDecimal() {
        assertFails { BigDecimal("1234567890.1234567890foo") }
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native implementation
    @Test
    fun testEquals() {
        val value = "0.340282366920938463463374607431768211456"
        assertEquals(BigDecimal(value), BigDecimal(value))
    }
}
