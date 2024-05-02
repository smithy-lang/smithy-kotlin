/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

// NOTE: We are restricted to what can be made available through D8 core library
// desugaring on Android
//
// See: https://developer.android.com/studio/write/java8-support-table

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.toKotlinDuration
import java.time.Duration as jtDuration
import java.time.Instant as jtInstant

public actual class Instant(internal val value: jtInstant) : Comparable<Instant> {
    public actual val epochSeconds: Long
        get() = value.epochSecond
    public actual val nanosecondsOfSecond: Int
        get() = value.nano

    actual override operator fun compareTo(other: Instant): Int = value.compareTo(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is Instant && this.value == other.value)

    override fun toString(): String = format(TimestampFormat.ISO_8601)

    /**
     * Returns an instant that is the result of adding the specified [duration] to this instant.
     *
     * If the [duration] is positive, the returned instant is later than this instant.
     * If the [duration] is negative, the returned instant is earlier than this instant.
     */
    public actual operator fun plus(duration: Duration): Instant = duration.toComponents { seconds, nanoseconds ->
        fromEpochSeconds(epochSeconds + seconds, nanosecondsOfSecond + nanoseconds)
    }

    /**
     * Returns an instant that is the result of subtracting the specified [duration] from this instant.
     *
     * If the [duration] is positive, the returned instant is earlier than this instant.
     * If the [duration] is negative, the returned instant is later than this instant.
     */
    public actual operator fun minus(duration: Duration): Instant = plus(-duration)

    public actual operator fun minus(other: Instant): Duration =
        jtDuration.between(other.value, value).toKotlinDuration()

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    public actual fun format(fmt: TimestampFormat): String = when (fmt) {
        TimestampFormat.ISO_8601 -> ISO_INSTANT.format(value.truncatedTo(ChronoUnit.MICROS))
        TimestampFormat.ISO_8601_CONDENSED -> ISO_8601_CONDENSED.format(value)
        TimestampFormat.ISO_8601_CONDENSED_DATE -> ISO_8601_CONDENSED_DATE.format(value)
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

    public actual companion object {

        private val RFC_5322_FIXED_DATE_TIME: DateTimeFormatter = buildRfc5322Formatter()

        private val utcZone = ZoneId.of("Z")

        private val ISO_8601_CONDENSED: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(utcZone)

        private val ISO_8601_CONDENSED_DATE: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(utcZone)

        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        public actual fun fromIso8601(ts: String): Instant {
            val parsed = parseIso8601(ts)
            return fromParsedDateTime(parsed)
        }

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        public actual fun fromRfc5322(ts: String): Instant {
            val parsed = parseRfc5322(ts)
            return fromParsedDateTime(parsed)
        }

        /**
         * Create an [Instant] from it's parts
         */
        public actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant =
            Instant(jtInstant.ofEpochSecond(seconds, ns.toLong()))

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        public actual fun fromEpochSeconds(ts: String): Instant = parseEpoch(ts)

        /**
         * Create an [Instant] from the current system time
         */
        public actual fun now(): Instant = Instant(jtInstant.now())

        /**
         * Create an [Instant] with the minimum possible value
         */
        public actual val MIN_VALUE: Instant = Instant(jtInstant.MIN)

        /**
         * Create an [Instant] with the maximum possible value
         */
        public actual val MAX_VALUE: Instant = Instant(jtInstant.MAX)
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
        parsed.ns,
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
private fun buildRfc5322Formatter(): DateTimeFormatter {
    // manually code maps to ensure correct data always used
    // (locale data can be changed by application code)
    val dow: Map<Long, String> = mapOf(
        1L to "Mon",
        2L to "Tue",
        3L to "Wed",
        4L to "Thu",
        5L to "Fri",
        6L to "Sat",
        7L to "Sun",
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
        12L to "Dec",
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
