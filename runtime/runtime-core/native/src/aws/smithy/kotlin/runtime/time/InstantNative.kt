/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import kotlinx.datetime.Clock
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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
            var parsed = DateTimeFormats.ISO_8601.parse(ts)

            // Handle leap seconds (23:59:59)
            parsed.second = parsed.second?.coerceAtMost(59)

            // Handle leap hours (24:00:00)
            return if (parsed.hour == 24) {
                parsed.hour = 0
                Instant(parsed.toInstantUsingOffset() + 1.days)
            } else {
                Instant(parsed.toInstantUsingOffset())
            }
        }

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        public actual fun fromRfc5322(ts: String): Instant = Instant(KtInstant.parse(ts, DateTimeFormats.RFC_5322))

        /**
         * Create an [Instant] from its parts
         */
        public actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant = Instant(KtInstant.fromEpochSeconds(seconds, ns))

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        @OptIn(FormatStringsInDatetimeFormats::class)
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
    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + epochSeconds.hashCode()
        result = 31 * result + nanosecondsOfSecond
        return result
    }
}
