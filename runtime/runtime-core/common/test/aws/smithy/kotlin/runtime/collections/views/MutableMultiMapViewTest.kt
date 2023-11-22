/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

import aws.smithy.kotlin.runtime.collections.MutableMultiMap
import aws.smithy.kotlin.runtime.collections.mutableMultiMapOf
import kotlin.test.Test
import kotlin.test.assertTrue

class MutableMultiMapViewTest {
    @Test
    fun testCompetingViews() {
        val src = mutableMultiMapOf<Int, String>()
        src.add(5, "five")

        val viewA = src.asView(stringify, parse, capitalize, decapitalize)
        val viewB = src.asView(multiply, divide, reverse, reverse)

        fun assertContains(key: Int, value: String) {
            assertTrue(src.contains(key, value), "Expected src to contain $key→$value")

            val aKey = stringify(key)
            val aVal = capitalize(value)
            assertTrue(viewA.contains(aKey, aVal), "Expected viewA to contain $aKey→$aVal")

            val bKey = multiply(key)
            val bVal = reverse(value)
            assertTrue(viewB.contains(bKey, bVal), "Expected viewB to contain $bKey→$bVal")
        }

        try {
            assertContains(5, "five")

            viewA.add("5", "CINCO")
            assertContains(5, "five")
            assertContains(5, "cinco")

            viewB.add(500, "qnic")
            assertContains(5, "five")
            assertContains(5, "cinco")
            assertContains(5, "cinq")

            viewA.add("8", "EIGHT")
            assertContains(8, "eight")

            viewB.add(800, "ohco")
            assertContains(8, "eight")
            assertContains(8, "ocho")

            src.add(8, "huit")
            assertContains(8, "eight")
            assertContains(8, "ocho")
            assertContains(8, "huit")
        } catch (e: AssertionError) {
            println("State of src   : ${src.dumpMultiMap()}")
            println("State of viewA : ${viewA.dumpMultiMap()}")
            println("State of viewB : ${viewB.dumpMultiMap()}")
            throw e
        }
    }
}

private fun MutableMultiMap<*, *>.dumpMultiMap() = entries.joinToString(", ", "{ ", " }") { (key, values) ->
    val valuesString = values.joinToString(", ", "[ ", " ]")
    "$key: $valuesString"
}

private val stringify: (Int) -> String = Int::toString
private val parse: (String) -> Int = String::toInt

private val capitalize: (String) -> String = String::uppercase
private val decapitalize: (String) -> String = String::lowercase

private val multiply: (Int) -> Int = { it * 100 }
private val divide: (Int) -> Int = { it / 100 }

private val reverse: (String) -> String = String::reversed
