// ktlint-disable filename
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

// NOTE: We are restricted to what can be made available through D8 core library
// desugaring on Android
//
// See: https://developer.android.com/studio/write/java8-support-table

import java.time.Instant as jtInstant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField

actual class Instant(internal val value: jtInstant) : Comparable<Instant> {
    actual val epochSeconds: Long
        get() = value.epochSecond
    actual val nanosecondsOfSecond: Int
        get() = value.nano

    actual override operator fun compareTo(other: Instant): Int = value.compareTo(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun equals(other: Any?): Boolean = value.equals(other)

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    actual fun format(fmt: TimestampFormat): String = when (fmt) {
        TimestampFormat.ISO_8601 -> ISO_INSTANT.format(value)
        TimestampFormat.RFC_5322 -> RFC_5322_FIXED_DATE_TIME.format(ZonedDateTime.ofInstant(value, ZoneOffset.UTC))
        TimestampFormat.EPOCH_SECONDS -> {
            val sb = StringBuffer("$epochSeconds")
            if (nanosecondsOfSecond > 0) {
                sb.append(".")
                val ns = "$nanosecondsOfSecond"
                val leadingZeros = "0".repeat(9 - ns.length)
                sb.append(leadingZeros)
                sb.append(ns)
                sb.trimEnd('0').toString()
            } else {
                sb.toString()
            }
        }
    }

    actual companion object {

        private val RFC_5322_FIXED_DATE_TIME: DateTimeFormatter = buildRfc5322Formatter()

        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        actual fun fromIso8601(ts: String): Instant {
            val parsed = parseIso8601(ts)
            return fromParsedDateTime(parsed)
        }

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        actual fun fromRfc5322(ts: String): Instant {
            val parsed = parseRfc5322(ts)
            return fromParsedDateTime(parsed)
        }

        /**
         * Create an [Instant] from it's parts
         */
        actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant =
            Instant(jtInstant.ofEpochSecond(seconds, ns.toLong()))
    }
}

private fun fromParsedDateTime(parsed: ParsedDatetime): Instant {
    val (dayOffset, hour, min, sec) = if (parsed.hour == 24 && parsed.min == 0 && parsed.sec == 0) {
        // midnight
        listOf(1, 0, 0, 0)
    } else if (parsed.hour == 23 && parsed.min == 59 && parsed.sec == 60) {
        // parsed a leap second - drop (LocalDateTime does not support leap seconds)
        // technically leap seconds are only scheduled for June 30 or Dec 31...
        listOf(0, 23, 59, 59)
    } else {
        listOf(0, parsed.hour, parsed.min, parsed.sec)
    }

    val ldt = LocalDateTime.of(
        parsed.year,
        parsed.month,
        parsed.day,
        hour,
        min,
        sec,
        parsed.ns).plusDays(dayOffset.toLong())
    val tzOffset = ZoneOffset.ofTotalSeconds(parsed.offsetSec)
    val odt = ldt.atOffset(tzOffset)
    val asInstant = odt.toInstant()
    return Instant(asInstant)
}

/**
 * Build and return a [DateTimeFormatter] for RFC5322
 *
 * tl;dr The single digit day-of-month is allowed by the spec but not desired by us.
 *
 * DateTimeFormatter.RFC_1123_DATE_TIME is _REALLY_ close to what we want but alas it will
 * format day of the month with a single digit when < 10 instead of the fixed 2DIGIT format
 * required by Smithy http-date timestampFormat trait (which is a specific fixed length
 * version of RFC5322).
 *
 * See also: https: *tools.ietf.org/html/rfc7231.html#section-7.1.1.1
 */
fun buildRfc5322Formatter(): DateTimeFormatter {
    // manually code maps to ensure correct data always used
    // (locale data can be changed by application code)
    val dow: Map<Long, String> = mapOf(
        1L to "Mon",
        2L to "Tue",
        3L to "Wed",
        4L to "Thu",
        5L to "Fri",
        6L to "Sat",
        7L to "Sun"
    )

    val moy: Map<Long, String> = mapOf(
        1L to "Jan",
        2L to "Feb",
        3L to "Mar",
        4L to "Apr",
        5L to "May",
        6L to "Jun",
        7L to "Jul",
        8L to "Aug",
        9L to "Sep",
        10L to "Oct",
        11L to "Nov",
        12L to "Dec"
    )

    val formatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .parseLenient()
        .optionalStart()
        .appendText(ChronoField.DAY_OF_WEEK, dow)
        .appendLiteral(", ")
        .optionalEnd()
        .appendValue(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral(' ')
        .appendText(ChronoField.MONTH_OF_YEAR, moy)
        .appendLiteral(' ')
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral(' ')
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalEnd()
        .appendLiteral(' ')
        .appendOffset("+HHMM", "GMT")
        .toFormatter()

    return formatter.withChronology(IsoChronology.INSTANCE)
}
