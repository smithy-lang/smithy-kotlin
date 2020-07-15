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

class ParseIso8601Test {

    private val extendedFmtTests = listOf(
        // no offset
        ParseTest("1990-02-17T02:31:22Z", 1990, 2, 17, 2, 31, 22, 0),
        // little z
        ParseTest("1990-02-17T02:31:22z", 1990, 2, 17, 2, 31, 22, 0),
        // missing time (basic iso8601 date)
        ParseTest("1990-02-17", 1990, 2, 17, 0, 0, 0, 0),
        // fractional seconds
        ParseTest("1990-02-17T02:31:22.1Z", 1990, 2, 17, 2, 31, 22, 100_000_000),
        ParseTest("1990-02-17T02:31:22.12Z", 1990, 2, 17, 2, 31, 22, 120_000_000),
        ParseTest("1990-02-17T02:31:22.123Z", 1990, 2, 17, 2, 31, 22, 123_000_000),
        ParseTest("1990-02-17T02:31:22.1234Z", 1990, 2, 17, 2, 31, 22, 123_400_000),
        ParseTest("1990-02-17T02:31:22.12345Z", 1990, 2, 17, 2, 31, 22, 123_450_000),
        ParseTest("1990-02-17T02:31:22.123456Z", 1990, 2, 17, 2, 31, 22, 123_456_000),
        ParseTest("1990-02-17T02:31:22.1234567Z", 1990, 2, 17, 2, 31, 22, 123_456_700),
        ParseTest("1990-02-17T02:31:22.12345678Z", 1990, 2, 17, 2, 31, 22, 123_456_780),
        ParseTest("1990-02-17T02:31:22.123456789Z", 1990, 2, 17, 2, 31, 22, 123_456_789),
        ParseTest("1990-02-17T02:31:22.000000001Z", 1990, 2, 17, 2, 31, 22, 1),
        ParseTest("1990-02-17T02:31:22.010002030Z", 1990, 2, 17, 2, 31, 22, 10_002_030),
        // - offset
        ParseTest("1990-12-19T16:39:57-08:00", 1990, 12, 19, 16, 39, 57, 0, -8 * 3600),
        ParseTest("1990-12-19T16:39:57-00:02", 1990, 12, 19, 16, 39, 57, 0, -2 * 60),
        // + offset
        ParseTest("1990-12-19T16:39:57+08:00", 1990, 12, 19, 16, 39, 57, 0, 8 * 3600),
        ParseTest("1990-12-19T16:39:57+00:02", 1990, 12, 19, 16, 39, 57, 0, 2 * 60)
    )

    @Test
    fun `it parses extended format timestamps`() {
        for ((idx, test) in extendedFmtTests.withIndex()) {
            val actual = parseIso8601(test.input)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    private val basicFmtTests = listOf(
        // with and without ':' separators
        ParseTest("20201105T02:31:22Z", 2020, 11, 5, 2, 31, 22, 0),
        ParseTest("20201105T023122Z", 2020, 11, 5, 2, 31, 22, 0),
        // missing time (basic iso8601 date)
        ParseTest("20201105", 2020, 11, 5, 0, 0, 0, 0),
        // fractional seconds (with and without ':' separators)
        ParseTest("20201105T02:31:22.1Z", 2020, 11, 5, 2, 31, 22, 100_000_000),
        ParseTest("20201105T023122.1Z", 2020, 11, 5, 2, 31, 22, 100_000_000),
        ParseTest("20201105T02:31:22.12Z", 2020, 11, 5, 2, 31, 22, 120_000_000),
        ParseTest("20201105T023122.12Z", 2020, 11, 5, 2, 31, 22, 120_000_000)
    )

    @Test
    fun `it parses basic format timestamps`() {
        for ((idx, test) in basicFmtTests.withIndex()) {
            val actual = parseIso8601(test.input)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    private val invalidTimestamps = listOf(
        // invalid year
        ParseErrorTest("201-07-22T03:30:00Z", "error at 0: expected exactly 4 digits; found 3"),
        // invalid month
        ParseErrorTest("2017-7-22T03:30:00Z", "error at 5: expected exactly 2 digits; found 1"),

        // invalid day
        ParseErrorTest("2017-07-022T03:30:00Z", "error at 10: expected one of `Tt"),

        // invalid hour - failure will come trying to parse the minute (sees 3:)
        ParseErrorTest("2017-07-22T033:30:00Z", "error at 13: expected exactly 2 digits; found 1"),

        // invalid min (0:)
        ParseErrorTest("2017-07-22T03:0:00Z", "error at 14: expected exactly 2 digits; found 1"),
        // missing minutes
        ParseErrorTest("2017-07-22T03::00Z", "error at 14: expected exactly 2 digits; found 0"),
        // non-digit (0f)
        ParseErrorTest("2017-07-22T03:0f:00Z", "error at 14: expected exactly 2 digits; found 1"),
        // invalid sec - failure will come trying to parse the timezone (sees 2 instead of Zz|+-HH:MM)
        ParseErrorTest("2017-07-22T03:30:002Z", "error at 19: invalid timezone offset"),
        // invalid sec - expected 2 digits
        ParseErrorTest("2017-07-22T03:30:0Z", "error at 17: expected exactly 2 digits; found 1"),
        // invalid nanosec - failure again comes trying to parse the timezone after successfully parsing 9 digits of nanosec
        ParseErrorTest("2017-07-22T03:30:02.1234567891Z", "error at 29: invalid timezone offset")
    )

    @Test
    fun `it rejects invalid extended timestamps`() {
        for ((idx, test) in invalidTimestamps.withIndex()) {
            val ex = assertFailsWith<ParseException>("test[$idx]: expected exception parsing ${test.input}") {
                parseIso8601(test.input)
            }
            ex.message!!.shouldContain(test.expectedMessage)
        }
    }
}
