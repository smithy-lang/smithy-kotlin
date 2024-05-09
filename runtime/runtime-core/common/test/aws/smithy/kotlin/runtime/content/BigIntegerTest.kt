/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.text.encoding.decodeHexBytes
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import kotlinx.coroutines.test.runTest
import kotlin.math.pow
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

//    @Test
//    fun testBytes() {
//        val x = BigInteger("340282366920938463463374607431768211456") // hex: 100000000000000000000000000000000
//        val xStr = x.toString()
//
//        var binary = decimalToBinary(xStr)
//        assertEquals("100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", binary)
//
//        if (binary.length % 4 != 0) {
//            binary = "0".repeat(4 - binary.length % 4) + binary
//        }
//
//        val hex = binary.chunked(4) {
//            it.toString().toInt(2).toString(16)
//        }.joinToString("")
//
//        assertEquals("100000000000000000000000000000000", hex)
//    }

    fun decimalToBinary(decimalStr: String): String {
        var decimal = decimalStr
        val binary = StringBuilder()
        while (decimal != "0") {
            val temp = StringBuilder()
            var carry = 0
            for (char in decimal) {
                val num = carry * 10 + (char - '0')
                temp.append(num / 2)
                carry = num % 2
            }
            binary.insert(0, carry) // Append the remainder to the binary result
            decimal = if (temp[0] == '0' && temp.length > 1) temp.substring(1).toString() else temp.toString()
            if (decimal.matches(Regex("0+"))) { // All zeros
                decimal = "0"
            }
        }
        return binary.toString()
    }

    // Converts a [BigInteger] to a [ByteArray].
    private fun BigInteger.toByteArray(): ByteArray {
        var decimal = this.toString()
        val binary = StringBuilder()
        while (decimal != "0") {
            val temp = StringBuilder()
            var carry = 0
            for (c in decimal) {
                val num = carry * 10 + c.code
                temp.append(num / 2)
                carry = num % 2
            }
            binary.insert(0, carry)

            decimal = if (temp[0] == '0' && temp.length > 1) {
                temp.substring(1)
            } else {
                temp.toString()
            }

            if (decimal.all { it == '0' }) { decimal = "0" }
        }

        return binary
            .padStart(8 - binary.length % 8, '0') // ensure binary string is zero-padded
            .chunked(8) { it.toString().toByte() } // convert each set of 8 bits to a byte
            .toByteArray()
    }

//    @Test
//    fun testByteArrayToBigInteger() = runTest {
//        val bytes = BigInteger("18446744073709551616").toByteArray()
//        val bigInt = bytes.toBigInteger()

//        println(bigInt.toString())
//    }
}
