/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

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

    @Test
    fun testEquals() {
        val value = "0.340282366920938463463374607431768211456"
        assertEquals(BigDecimal(value), BigDecimal(value))
    }

    @Test
    fun testExponents() {
        var counter = 0
        data class TestCase(val value: String, val mantissa: BigInteger, val exponent: Int, val id: Int = ++counter)

        val tests = listOf(
            TestCase("0.0", BigInteger("0"), 0),
            TestCase("1.0", BigInteger("1"), 0),
            TestCase("10.0", BigInteger("1"), 1),
            TestCase("11.0", BigInteger("11"), 1),
            TestCase("0.1", BigInteger("1"), -1),
        )

        val failed = tests.flatMap { (value, mantissa, exponent, id) ->
            val bd = BigDecimal(value)
            listOfNotNull(
                runCatching { assertEquals(mantissa, bd.mantissa, "#$id Wrong mantissa") }.exceptionOrNull(),
                runCatching { assertEquals(exponent, bd.exponent, "#$id Wrong exponent") }.exceptionOrNull(),
            )
        }

        if (failed.isNotEmpty()) {
            val msg = failed.joinToString("\n  ", "Failed ${failed.size}/${tests.size} tests:\n  ") { it.message ?: "(null)" }
            throw AssertionError(msg).apply {
                failed.forEach { addSuppressed(it) }
            }
        }
    }

    @Test
    fun testExponent() {
        val mantissa = BigInteger("123456789")
        val exponent = 10
        val value = BigDecimal(mantissa, exponent)
        assertEquals("1.23456789E+10", value.toString())
        assertEquals(mantissa, value.mantissa)
        assertEquals(10, value.exponent)

        assertEquals("12345678900", value.toPlainString())
    }
}
