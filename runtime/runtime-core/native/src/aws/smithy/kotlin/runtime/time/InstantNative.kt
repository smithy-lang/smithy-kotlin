/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlin.time.Duration
import kotlinx.datetime.Instant as KtInstant

private fun TimestampFormat.asDateTimeFormat() = when (this) {
    TimestampFormat.RFC_5322 -> DateTimeFormats.RFC_5322
    TimestampFormat.ISO_8601_FULL -> DateTimeFormats.ISO_8601
    TimestampFormat.ISO_8601_CONDENSED -> DateTimeFormats.ISO_8601_CONDENSED
    TimestampFormat.ISO_8601_CONDENSED_DATE -> DateTimeFormats.ISO_8601_CONDENSED_DATE
    else -> throw IllegalArgumentException("TimestampFormat $this could not be converted to a DateTimeFormat")
}

private fun KtInstant.truncateToMicros(): KtInstant = KtInstant.fromEpochSeconds(epochSeconds, nanosecondsOfSecond / 1_000 * 1_000)

public actual class Instant(internal val delegate: KtInstant) : Comparable<Instant> {

    actual override fun compareTo(other: Instant): Int = delegate.compareTo(other.delegate)

    public actual val epochSeconds: Long = delegate.epochSeconds
    public actual val nanosecondsOfSecond: Int = delegate.nanosecondsOfSecond

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    public actual fun format(fmt: TimestampFormat): String = when (fmt) {
        TimestampFormat.ISO_8601 -> delegate.truncateToMicros().format(DateTimeFormats.ISO_8601)
        TimestampFormat.EPOCH_SECONDS -> {
            val s = delegate.epochSeconds.toString()
            val ns = if (delegate.nanosecondsOfSecond != 0) {
                ".${delegate.nanosecondsOfSecond.toString().padStart(9, '0').trimEnd('0')}"
            } else {
                ""
            }
            s + ns
        }
        else -> delegate.format(fmt.asDateTimeFormat())
    }

    /**
     * Returns an instant that is the result of adding the specified [duration] to this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be earlier than this Instant.
     */
    public actual operator fun plus(duration: Duration): Instant = Instant(delegate + duration)

    /**
     * Returns an instant that is the result of subtracting the specified [duration] from this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be later than this Instant.
     */
    public actual operator fun minus(duration: Duration): Instant = Instant(delegate - duration)

    public actual operator fun minus(other: Instant): Duration = delegate - other.delegate

    public actual companion object {
        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        public actual fun fromIso8601(ts: String): Instant {
            val parseException =  ParseException(ts, "Failed to parse $ts into an ISO-8601 timestamp", 0)

            listOf(
                { DateTimeFormats.ISO_8601.parse(ts).apply { if (second == 60) second = 59 }.toInstantUsingOffset() },
                { KtInstant.parse(ts, DateTimeFormats.ISO_8601_CONDENSED) },
                { LocalDate.parse(ts, ISO_8601_CONDENSED_DATE_LOCALDATE).atStartOfDayIn(TimeZone.UTC) },
            ).forEach { parseFn ->
                try {
                    return Instant(parseFn())
                } catch (e: IllegalArgumentException) {
                    parseException.addSuppressed(e)
                }
            }

            throw parseException
        }

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        public actual fun fromRfc5322(ts: String): Instant = try {
            Instant(KtInstant.parse(ts, DateTimeFormats.RFC_5322))
        } catch (e: IllegalArgumentException) {
            throw ParseException(ts, "Failed to parse $ts into an RFC-5322 timestamp", 0)
        }

        /**
         * Create an [Instant] from its parts
         */
        public actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant = try {
            Instant(KtInstant.fromEpochSeconds(seconds, ns))
        } catch (e: IllegalArgumentException) {
            throw ParseException("${seconds}s, ${ns}ns", "Failed to parse (${seconds}s, ${ns}ns) into an epoch seconds timestamp", 0)
        }

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        public actual fun fromEpochSeconds(ts: String): Instant = fromEpochSeconds(ts.toLong(), 0)

        /**
         * Create an [Instant] from the current system time
         */
        public actual fun now(): Instant = Instant(Clock.System.now())

        /**
         * Create an [Instant] with the minimum possible value
         */
        public actual val MIN_VALUE: Instant = Instant(KtInstant.DISTANT_PAST)

        /**
         * Create an [Instant] with the maximum possible value
         */
        public actual val MAX_VALUE: Instant = Instant(KtInstant.DISTANT_FUTURE)
    }

    public override fun equals(other: Any?): Boolean = other is Instant && delegate == other.delegate
    public override fun toString(): String = delegate.toString()
    public override fun hashCode(): Int = delegate.hashCode()
}
