// ktlint-disable filename
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.time

// NOTE: We are restricted to what can be made available through D8 core library
// desugaring on Android
//
// See: https://developer.android.com/studio/write/java8-support-table

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.Instant as jtInstant

actual class Instant(internal val value: jtInstant) : Comparable<Instant> {
    actual val epochSeconds: Long
        get() = value.epochSecond
    actual val nanosecondsOfSecond: Int
        get() = value.nano

    actual override operator fun compareTo(other: Instant): Int = value.compareTo(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is Instant && this.value == other.value)

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    actual fun format(fmt: TimestampFormat): String = when (fmt) {
        TimestampFormat.ISO_8601 -> ISO_INSTANT.format(value)
        TimestampFormat.RFC_5322 -> RFC_5322_FIXED_DATE_TIME.format(ZonedDateTime.ofInstant(value, ZoneOffset.UTC))
        TimestampFormat.EPOCH_SECONDS -> toEpochSecondsString()
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

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        actual fun fromEpochSeconds(ts: String): Instant = parseEpoch(ts)

        /**
         * Create an [Instant] from the current system time
         */
        actual fun now(): Instant = Instant(jtInstant.now())
    }
}

private fun fromParsedDateTime(parsed: ParsedDatetime): Instant {
    val (dayOffset, hour, min, sec) = parsed.unpackDayOffset()

    val ldt = LocalDateTime.of(
        parsed.year,
        parsed.month,
        parsed.day,
        hour,
        min,
        sec,
        parsed.ns
    ).plusDays(dayOffset.toLong())
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
    val formatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .parseLenient()
        .optionalStart()
        .appendText(ChronoField.DAY_OF_WEEK, DAY_OF_WEEK)
        .appendLiteral(", ")
        .optionalEnd()
        .appendValue(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral(' ')
        .appendText(ChronoField.MONTH_OF_YEAR, MON_OF_YEAR)
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
