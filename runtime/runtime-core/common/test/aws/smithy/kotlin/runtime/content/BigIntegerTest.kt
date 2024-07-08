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

    @Test
    fun testPlusOperator() {
        // Map of an expected value to a pair of two values that should sum to get that expected value
        val tests = mapOf<String, Pair<String, String>>(
            "0" to ("-1" to "1"),
            "1" to ("-1" to "2"),
            "340282366920938463463374607431768211456" to ("340282366920938463463374607431768211446" to "10"),
            "-32134902384590238490284023839028330923830129830129301234239834982" to ("-42134902384590238490284023839028330923830129830129301234239834982" to "10000000000000000000000000000000000000000000000000000000000000000")
        )

        tests.forEach { (expected, actualPair) ->
            val a = BigInteger(actualPair.first)
            val b = BigInteger(actualPair.second)

            assertEquals(expected, (a + b).toString())
        }
    }

    @Test
    fun testMinusOperator() {
        // Map of an expected value to a pair of two values that should subtract to get that expected value
        val tests = mapOf<String, Pair<String, String>>(
            "-2" to ("-1" to "1"),
            "-3" to ("-1" to "2"),
            "340282366920938463463374607431768211436" to ("340282366920938463463374607431768211446" to "10"),
            "-52134902384590238490284023839028330923830129830129301234239834982" to ("-42134902384590238490284023839028330923830129830129301234239834982" to "10000000000000000000000000000000000000000000000000000000000000000")
        )

        tests.forEach { (expected, actualPair) ->
            val a = BigInteger(actualPair.first)
            val b = BigInteger(actualPair.second)

            assertEquals(expected, (a - b).toString())
        }
    }
}
