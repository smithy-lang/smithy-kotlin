/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

import software.aws.clientrt.time.externals.*
import software.aws.clientrt.time.externals.Instant as jsInstant

actual class Instant(internal val value: jsInstant) : Comparable<Instant> {
    actual val epochSeconds: Long = value.epochSecond().toLong()

    actual val nanosecondsOfSecond: Int = value.nano().toInt()

    actual override fun compareTo(other: Instant): Int = value.compareTo(other.value).toInt()

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    actual fun format(fmt: TimestampFormat): String = when (fmt) {
        TimestampFormat.ISO_8601 -> DateTimeFormatter.ISO_INSTANT.format(value)
        TimestampFormat.RFC_5322 -> toRfc5322String()
        TimestampFormat.EPOCH_SECONDS -> toEpochSecondsString()
    }

    actual companion object {

        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        actual fun fromIso8601(ts: String): Instant = parseIso8601(ts).toInstant()

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        actual fun fromRfc5322(ts: String): Instant = parseRfc5322(ts).toInstant()

        /**
         * Create an [Instant] from its parts
         */
        actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant = Instant(jsInstant.ofEpochSecond(seconds, ns))

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        actual fun fromEpochSeconds(ts: String): Instant = parseEpoch(ts)

        /**
         * Create an [Instant] from the current system time
         */
        actual fun now(): Instant = Instant(jsInstant.now())
    }
}

private fun ParsedDatetime.toInstant(): Instant {
    val (dayOffset, hour, min, sec) = unpackDayOffset()
    val ldt = LocalDateTime.of(this.year, this.month, this.day, hour, min, sec, this.ns).plusDays(dayOffset)
    val tzOffset = ZoneOffset.ofTotalSeconds(this.offsetSec)
    val odt = ldt.atOffset(tzOffset)
    return Instant(odt.toInstant())
}

private fun Instant.toRfc5322String(): String {
    val zoned = ZonedDateTime.ofInstant(value, ZoneId.UTC)
    return StringBuilder()
        .append(DAY_OF_WEEK.getValue(zoned.dayOfWeek().value().toLong()))
        .append(", ")
        .append(zoned.dayOfMonth().toString().padStart(2, '0'))
        .append(" ")
        .append(MON_OF_YEAR.getValue(zoned.monthValue().toLong()))
        .append(" ")
        .append(zoned.year())
        .append(" ")
        .append(zoned.hour().toString().padStart(2, '0'))
        .append(":")
        .append(zoned.minute().toString().padStart(2, '0'))
        .append(":")
        .append(zoned.second().toString().padStart(2, '0'))
        .append(" GMT")
        .toString()
}
