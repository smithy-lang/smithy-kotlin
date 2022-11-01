/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

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

    /**
     * Conversion from epoch sec/ns to multiple formats of ISO-8601
     */
    private data class Iso8601FmtTest(
        val sec: Long,
        val ns: Int,
        val expectedIso8601: String,
        val expectedIso8601Cond: String,
        val expectedIso8601CondDate: String,
    )

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
        FromTest("2020-11-04T24:00:00Z", 1604534400, 0),
    )

    @Test
    fun testFromIso8601() {
        for ((idx, test) in iso8601Tests.withIndex()) {
            val actual = Instant.fromIso8601(test.input)
            assertEquals(test.expectedSec, actual.epochSeconds, "test[$idx]: failed to correctly parse ${test.input}")
            assertEquals(test.expectedNs, actual.nanosecondsOfSecond, "test[$idx]: failed to correctly parse ${test.input}")
        }
    }

    private val iso8601FmtTests = listOf(
        Iso8601FmtTest(1604604157, 0, "2020-11-05T19:22:37Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604604157, 422_000_000, "2020-11-05T19:22:37.422Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604604157, 422_000, "2020-11-05T19:22:37.000422Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604604157, 1, "2020-11-05T19:22:37Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604604157, 999, "2020-11-05T19:22:37Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604604157, 1_000, "2020-11-05T19:22:37.000001Z", "20201105T192237Z", "20201105"),
        Iso8601FmtTest(1604602957, 0, "2020-11-05T19:02:37Z", "20201105T190237Z", "20201105"),
        Iso8601FmtTest(1604605357, 0, "2020-11-05T19:42:37Z", "20201105T194237Z", "20201105"),
        Iso8601FmtTest(1604558257, 0, "2020-11-05T06:37:37Z", "20201105T063737Z", "20201105"),
        Iso8601FmtTest(1604650057, 0, "2020-11-06T08:07:37Z", "20201106T080737Z", "20201106"),
    )

    private val iso8601Forms = mapOf(
        TimestampFormat.ISO_8601 to Iso8601FmtTest::expectedIso8601,
        TimestampFormat.ISO_8601_CONDENSED to Iso8601FmtTest::expectedIso8601Cond,
        TimestampFormat.ISO_8601_CONDENSED_DATE to Iso8601FmtTest::expectedIso8601CondDate,
    )

    @Test
    fun testFormatAsIso8601() {
        for ((idx, test) in iso8601FmtTests.withIndex()) {
            for ((format, getter) in iso8601Forms) {
                val actual = Instant
                    .fromEpochSeconds(test.sec, test.ns)
                    .format(format)
                val expected = getter(test)
                assertEquals(expected, actual, "test[$idx]: failed to correctly format Instant as $format")
            }
        }
    }

    private val rfc5322Tests = listOf(
        FromTest("Thu, 05 Nov 2020 19:22:37 +0000", 1604604157, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -0000", 1604604157, 0),
        // with offsets
        FromTest("Thu, 05 Nov 2020 19:22:37 +0020", 1604602957, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -0020", 1604605357, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 +1245", 1604558257, 0),
        FromTest("Thu, 05 Nov 2020 19:22:37 -1245", 1604650057, 0),
    )

    @Test
    fun testFromRfc5322() {
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
        FmtTest(1604650057, 0, "Fri, 06 Nov 2020 08:07:37 GMT"),
    )

    @Test
    fun testFormatAsRfc5322() {
        for ((idx, test) in rfc5322FmtTests.withIndex()) {
            val actual = Instant
                .fromEpochSeconds(test.sec, test.ns)
                .format(TimestampFormat.RFC_5322)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly format Instant from")
        }
    }

    private val epochFmtTests = listOf(
        FmtTest(1604604157, 0, "1604604157"),
        FmtTest(1604604157, 345, "1604604157.000000345"),
        FmtTest(1604604157, 34_500, "1604604157.0000345"),
        FmtTest(1604604157, 345_000_000, "1604604157.345"),
        FmtTest(1604604157, 345_006_000, "1604604157.345006"),
    )

    @Test
    fun testFormatAsEpochSeconds() {
        for ((idx, test) in epochFmtTests.withIndex()) {
            val actual = Instant
                .fromEpochSeconds(test.sec, test.ns)
                .format(TimestampFormat.EPOCH_SECONDS)
            assertEquals(test.expected, actual, "test[$idx]: failed to correctly format Instant from")
        }
    }

    @Test
    fun testToEpochDouble() {
        val sec = 1604604157L
        val ns = 12_345_000
        val actual = Instant.fromEpochSeconds(sec, ns).toEpochDouble()
        assertEquals(sec, actual.toLong())
        val fracSecs: Double = actual - sec
        assertTrue(kotlin.math.abs(0.012345 - fracSecs) < 0.00001)
    }

    @Test
    fun testGetCurrentTime() {
        val currentTime = Instant.now()

        val pastInstant = 1602099269 // 2020-10-07T19:34:29+00:00
        // Arrow of time ensures this test shall always pass with a valid clock
        assertTrue(currentTime.epochSeconds > pastInstant)
    }

    @Test
    fun testGetEpochMilliseconds() {
        val instant = Instant.fromEpochSeconds(1602878160, 200_000)
        val expected = 1602878160000L
        assertEquals(expected, instant.epochMilliseconds)

        val instantWithMilli = Instant.fromEpochSeconds(1602878160, 2_000_000)
        val expected2 = 1602878160002L
        assertEquals(expected2, instantWithMilli.epochMilliseconds)
    }

    @Test
    fun testFromEpochMilliseconds() {
        val ts1 = 1602878160000L
        val expected = Instant.fromEpochSeconds(1602878160, 0)
        assertEquals(expected, Instant.fromEpochMilliseconds(ts1))

        val ts2 = 1602878160002L
        val expected2 = Instant.fromEpochSeconds(1602878160, 2_000_000)
        assertEquals(expected2, Instant.fromEpochMilliseconds(ts2))
    }

    // Select tests pulled from edge cases/tickets in the V2 Java SDK.
    // Always good to learn from others...
    class V2JavaSdkTests {
        @Test
        fun v2JavaSdkTt0031561767() {
            val input = "Fri, 16 May 2014 23:56:46 GMT"
            val instant: Instant = Instant.fromRfc5322(input)
            assertEquals(input, instant.format(TimestampFormat.RFC_5322))
        }

        /**
         * Tests the Date marshalling and unmarshalling. Asserts that the value is
         * same before and after marshalling/unmarshalling
         */
        @Test
        fun v2JavaSdkUnixTimestampRoundtrip() {
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

    @Test
    fun testPlusMinusDuration() {
        val start = Instant.fromEpochSeconds(1000, 1000)

        val offset = 10.seconds + 1000.nanoseconds
        assertEquals(Instant.fromEpochSeconds(1010, 2000), start + offset)
        assertEquals(Instant.fromEpochSeconds(990, 0), start - offset)
    }

    @Test
    fun testRoundTripUtcOffset() {
        // sanity check we only ever emit UTC timestamps (e.g. round trip a response with UTC offset)
        val tests = listOf(
            "2020-11-05T19:22:37+00:20" to "2020-11-05T19:02:37Z",
            "2020-11-05T19:22:37-00:20" to "2020-11-05T19:42:37Z",
            "2020-11-05T19:22:37+12:45" to "2020-11-05T06:37:37Z",
            "2020-11-05T19:22:37-12:45" to "2020-11-06T08:07:37Z",
            "2020-11-05T19:22:37.345-12:45" to "2020-11-06T08:07:37.345Z",
        )

        tests.forEachIndexed { idx, test ->
            val parsed = Instant.fromIso8601(test.first)
            val actual = parsed.format(TimestampFormat.ISO_8601)
            assertEquals(test.second, actual, "test[$idx]: failed to format offset timestamp in UTC")
        }
    }
}
