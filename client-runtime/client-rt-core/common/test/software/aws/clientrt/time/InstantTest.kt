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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// tests for conversion from a parsed representation into an Instant instance

class InstantTest {

    /**
     * Conversion from a string to epoch sec/ns
     */
    private data class FromTest(val input: String, val expectedSec: Long, val expectedNs: Int)

    /**
     * Conversion from epoch sec/ns to a formatted string
     */
    private data class FmtTest(val sec: Long, val ns: Int, val expected: String)

    private val iso8601Tests = listOf(
        FromTest("2020-11-05T19:22:37+00:00", 1604604157, 0),
        FromTest("2020-11-05T19:22:37Z", 1604604157, 0),
        FromTest("2020-11-05T19:22:37.002Z", 1604604157, 2_000_000),
        // same but basic format
        FromTest("20201105T192237+00:00", 1604604157, 0),
        FromTest("20201105T192237Z", 1604604157, 0),
        FromTest("20201105T192237.002Z", 1604604157, 2_000_000),
        // with offsets
        FromTest("2020-11-05T19:22:37+00:20", 1604602957, 0),
        FromTest("2020-11-05T19:22:37-00:20", 1604605357, 0),
        FromTest("2020-11-05T19:22:37+12:45", 1604558257, 0),
        FromTest("2020-11-05T19:22:37-12:45", 1604650057, 0),
        FromTest("2020-11-05T19:22:37.345-12:45", 1604650057, 345_000_000),

        // leap second - dropped to: 2020-12-31T23:59:59
        FromTest("2020-12-31T23:59:60Z", 1609459199, 0),
        // midnight - should be 11/5 12AM
        FromTest("2020-11-04T24:00:00Z", 1604534400, 0)
    )

    @Test
    fun `test fromIso8601`() {
        for ((idx, test) in iso8601Tests.withIndex()) {
            val actual = Instant.fromIso8601(test.input)
            assertEquals(test.expectedSec, actual.epochSeconds, "test[$idx]: failed to correctly parse ${test.input}")
            assertEquals(test.expectedNs, actual.nanosecondsOfSecond, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    private val iso8601FmtTests = listOf(
        FmtTest(1604604157, 0, "2020-11-05T19:22:37Z"),
        FmtTest(1604604157, 422_000_000, "2020-11-05T19:22:37.422Z"),
        FmtTest(1604604157, 422_000, "2020-11-05T19:22:37.000422Z"),
        FmtTest(1604602957, 0, "2020-11-05T19:02:37Z"),
        FmtTest(1604605357, 0, "2020-11-05T19:42:37Z"),
        FmtTest(1604558257, 0, "2020-11-05T06:37:37Z"),
        FmtTest(1604650057, 0, "2020-11-06T08:07:37Z")
    )
    @Test
    fun `test format as iso8601`() {
        for ((idx, test) in iso8601FmtTests.withIndex()) {
            val actual = Instant
                .fromEpochSeconds(test.sec, test.ns)
                .format(TimestampFormat.ISO_8601)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly format Instant from")
        }
    }

    private val rfc5322Tests = listOf(
        FromTest("Thu, 05 Nov 2020 19:22:37 +0000", 1604604157, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -0000", 1604604157, 0),
        // with offsets
        FromTest("Thu, 05 Nov 2020 19:22:37 +0020", 1604602957, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -0020", 1604605357, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 +1245", 1604558257, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -1245", 1604650057, 0)
    )
    @Test
    fun `test fromRfc5322`() {
        for ((idx, test) in rfc5322Tests.withIndex()) {
            val actual = Instant.fromRfc5322(test.input)
            assertEquals(test.expectedSec, actual.epochSeconds, "test[$idx]: failed to correctly parse ${test.input}")
            assertEquals(test.expectedNs, actual.nanosecondsOfSecond, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    private val rfc5322FmtTests = listOf(
        FmtTest(1604604157, 0, "Thu, 05 Nov 2020 19:22:37 GMT"),
        FmtTest(1604602957, 0, "Thu, 05 Nov 2020 19:02:37 GMT"),
        FmtTest(1604605357, 0, "Thu, 05 Nov 2020 19:42:37 GMT"),
        FmtTest(1604558257, 0, "Thu, 05 Nov 2020 06:37:37 GMT"),
        FmtTest(1604650057, 0, "Fri, 06 Nov 2020 08:07:37 GMT")
    )
    @Test
    fun `test format as rfc5322`() {
        for ((idx, test) in rfc5322FmtTests.withIndex()) {
            val actual = Instant
                .fromEpochSeconds(test.sec, test.ns)
                .format(TimestampFormat.RFC_5322)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly format Instant from")
        }
    }

    @Test
    fun `test format as epoch seconds`() {
        val actual = Instant
            .fromEpochSeconds(1604604157, 0)
            .format(TimestampFormat.EPOCH_SECONDS)
        val expected = "1604604157"
        assertEquals(expected, actual)
    }

    @Test
    fun `test toEpochDouble`() {
        val sec = 1604604157L
        val ns = 12_345_000
        val actual = Instant.fromEpochSeconds(sec, ns).toEpochDouble()
        assertEquals(sec, actual.toLong())
        val fracSecs: Double = actual - sec
        assertTrue(kotlin.math.abs(0.012345 - fracSecs) < 0.00001)
    }

    // Select tests pulled from edge cases/tickets in the V2 Java SDK.
    // Always good to learn from others...
    class V2JavaSdkTests {
        @Test
        fun `v2 java sdk tt0031561767`() {
            val input = "Fri, 16 May 2014 23:56:46 GMT"
            val instant: Instant = Instant.fromRfc5322(input)
            assertEquals(input, instant.format(TimestampFormat.RFC_5322))
        }

        /**
         * Tests the Date marshalling and unmarshalling. Asserts that the value is
         * same before and after marshalling/unmarshalling
         */
        @Test
        fun `v2 java sdk UnixTimestampRoundtrip`() {
            // v2 sdk used currentTimeMillis(), instead we just hard code a value here
            // otherwise that would be a JVM specific test since since we do not (yet) have
            // a Kotlin MPP way of getting current timestamp. Also obviously not using epoch mill
            // but instead just epoch sec. Spirit of the test is the same though
            longArrayOf(1595016457, 1L, 0L)
                .map { Instant.fromEpochSeconds(0, 0) }
                .forEach { instant ->
                    val serverSpecificDateFormat: String = instant.format(TimestampFormat.EPOCH_SECONDS)
                    val parsed: Instant = parseEpoch(serverSpecificDateFormat)
                    assertEquals(instant.epochSeconds, parsed.epochSeconds)
                }
        }

        // NOTE: There is additional set of edge case tests related to a past issue
        // in DateUtilsTest.java in the v2 sdk. Specifically around
        // issue 223: https://github.com/aws/aws-sdk-java/issues/233
        //
        // (1) - That issue is about round tripping values between SDK versions
        // (2) - The input year in those tests is NOT valid and should never have
        //       been accepted by the parser.
    }
}
