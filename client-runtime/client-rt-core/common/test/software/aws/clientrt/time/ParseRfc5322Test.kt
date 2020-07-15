/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.time

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseRfc5322Test {
    private val validTests = listOf(
        // http-date: RFC7231
        // https://tools.ietf.org/html/rfc7231.html#section-7.1.1.1
        ParseTest("Sun, 06 Nov 1994 08:49:37 GMT", 1994, 11, 6, 8, 49, 37, 0, 0),
        // RFC5322 technically allows 1 OR 2 digit days
        ParseTest("Sun, 6 Nov 1994 08:49:37 GMT", 1994, 11, 6, 8, 49, 37, 0, 0),
        // optional seconds
        ParseTest("Sun, 06 Nov 1994 08:49 GMT", 1994, 11, 6, 8, 49, 0, 0, 0),
        // Optional dow
        ParseTest("06 Nov 1994 08:49:37 GMT", 1994, 11, 6, 8, 49, 37, 0, 0),

        // +- 0 offsets
        ParseTest("Sun, 06 Nov 1994 08:49:37 +0000", 1994, 11, 6, 8, 49, 37, 0, 0),
        ParseTest("Sun, 06 Nov 1994 08:49:37 -0000", 1994, 11, 6, 8, 49, 37, 0, 0),
        ParseTest("Sun, 06 Nov 1994 08:49:37 +0200", 1994, 11, 6, 8, 49, 37, 0, 2 * 3600),
        ParseTest("Sun, 06 Nov 1994 08:49:37 -0200", 1994, 11, 6, 8, 49, 37, 0, -2 * 3600),
        ParseTest("Sun, 06 Nov 1994 08:49:37 +0015", 1994, 11, 6, 8, 49, 37, 0, 15 * 60),
        ParseTest("Sun, 06 Nov 1994 08:49:37 -0015", 1994, 11, 6, 8, 49, 37, 0, -15 * 60),
        ParseTest("Sun, 06 Nov 1994 08:49:37 +1245", 1994, 11, 6, 8, 49, 37, 0, 12 * 3600 + 45 * 60),

        // obsolete zone names
        ParseTest("Sun, 06 Nov 1994 08:49:37 UTC", 1994, 11, 6, 8, 49, 37, 0, 0),
        ParseTest("Sun, 06 Nov 1994 08:49:37 UT", 1994, 11, 6, 8, 49, 37, 0, 0),
        ParseTest("Sun, 06 Nov 1994 08:49:37 Z", 1994, 11, 6, 8, 49, 37, 0, 0)
    )

    @Test
    fun `it parses rfc5322 timestamps`() {
        for ((idx, test) in validTests.withIndex()) {
            val actual = parseRfc5322(test.input)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    @Test
    fun `it parses all valid day names`() {
        val validDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        for (day in validDays) {
            val input = "$day, 06 Nov 1994 08:49:37 GMT"
            val test = ParseTest(input, 1994, 11, 6, 8, 49, 37, 0, 0)
            val actual = parseRfc5322(test.input)
            assertEquals(test.expected, actual, "test[$day]: failed to correctly parse ${test.input}")
        }
    }

    @Test
    fun `it parses all valid month names`() {
        val validMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        for ((idx, month) in validMonths.withIndex()) {
            val input = "Mon, 06 $month 1994 08:49:37 GMT"
            val test = ParseTest(input, 1994, idx + 1, 6, 8, 49, 37, 0, 0)
            val actual = parseRfc5322(test.input)
            assertEquals(test.expected, actual, "test[$month]: failed to correctly parse ${test.input}")
        }
    }

    private val invalidTimestamps = listOf(
        // invalid day
        ParseErrorTest("Ble, 06 Nov 1994 08:49:37 GMT", "error at 0: no alternatives matched"),
        // invalid month
        ParseErrorTest("Mon, 06 Ble 1994 08:49:37 GMT", "error at 8: invalid month `Ble`"),
        // missing ws
        ParseErrorTest("Mon,06 Nov 1994 08:49:37 GMT", "error at 4: expected ` ` found `0`"),
        // invalid day
        ParseErrorTest("Mon, 32 Nov 1994 08:49:37 GMT", "error at 5: 32 not in range 1..31"),
        // invalid year
        ParseErrorTest("Mon, 07 Nov 194 08:49:37 GMT", "error at 12: expected exactly 4 digits; found 3"),
        // invalid hour
        ParseErrorTest("Mon, 07 Nov 1994 8:49:37 GMT", "error at 17: expected exactly 2 digits; found 1"),
        // invalid min
        ParseErrorTest("Mon, 07 Nov 1994 14:2:37 GMT", "error at 20: expected exactly 2 digits; found 1"),
        ParseErrorTest("Mon, 07 Nov 1994 14:62:37 GMT", "error at 20: 62 not in range 0..59"),
        // invalid sec
        ParseErrorTest("Mon, 07 Nov 1994 14:02:7 GMT", "error at 23: expected exactly 2 digits; found 1"),
        ParseErrorTest("Mon, 07 Nov 1994 14:02:72 GMT", "error at 23: 72 not in range 0..60"),
        ParseErrorTest("Mon, 07 Nov 1994 14:02:02 +000", "error at 26: invalid timezone offset"),
        ParseErrorTest("Mon, 07 Nov 1994 14:02:02 EST", "error at 26: invalid timezone offset")
    )

    @Test
    fun `it rejects invalid timestamps`() {
        for ((idx, test) in invalidTimestamps.withIndex()) {
            val ex = assertFailsWith<ParseException>("test[$idx]: expected exception parsing ${test.input}") {
                parseRfc5322(test.input)
            }
            ex.message!!.shouldContain(test.expectedMessage)
        }
    }
}
