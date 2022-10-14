/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.time

import kotlin.time.Duration

// FIXME - remove in favor of kotlinx-datetime before GA (assuming it's available). For now
// we are stubbing this out for codegen purposes and supporting the various timestamp format parsers.
// the actual `Instant` class has additional methods users would actually want/need.

// nanoseconds/sec
internal const val NS_PER_SEC = 1_000_000_000

// ms/sec
internal const val MILLISEC_PER_SEC = 1_000

// ns/ms
internal const val NS_PER_MILLISEC = 1_000_000

// represents a moment on the UTC-SLS time scale
public expect class Instant : Comparable<Instant> {
    public val epochSeconds: Long
    public val nanosecondsOfSecond: Int

    override operator fun compareTo(other: Instant): Int

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    public fun format(fmt: TimestampFormat): String

    /**
     * Returns an instant that is the result of adding the specified [duration] to this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be earlier than this Instant.
     */
    public operator fun plus(duration: Duration): Instant

    /**
     * Returns an instant that is the result of subtracting the specified [duration] from this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be later than this Instant.
     */
    public operator fun minus(duration: Duration): Instant

    public companion object {
        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        public fun fromIso8601(ts: String): Instant

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        public fun fromRfc5322(ts: String): Instant

        /**
         * Create an [Instant] from its parts
         */
        public fun fromEpochSeconds(seconds: Long, ns: Int = 0): Instant

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        public fun fromEpochSeconds(ts: String): Instant

        /**
         * Create an [Instant] from the current system time
         */
        public fun now(): Instant

        /**
         * Create an [Instant] with the minimum possible value
         */
        public val MIN_VALUE: Instant

        /**
         * Create an [Instant] with the maximum possible value
         */
        public val MAX_VALUE: Instant

    }
}

/**
 * Convert [Instant] to a double representing seconds and milliseconds since the epoch
 */
public fun Instant.toEpochDouble(): Double = epochSeconds.toDouble() + (nanosecondsOfSecond.toDouble() / NS_PER_SEC)

/**
 * Get the epoch milliseconds representation of the [Instant]
 */
public val Instant.epochMilliseconds: Long
    get() = epochSeconds * MILLISEC_PER_SEC + (nanosecondsOfSecond / NS_PER_MILLISEC)

/**
 * Create an [Instant] from epoch millisecond timestamp
 */
public fun Instant.Companion.fromEpochMilliseconds(milliseconds: Long): Instant {
    val secs = milliseconds / MILLISEC_PER_SEC
    val ns = (milliseconds - secs * MILLISEC_PER_SEC) * NS_PER_MILLISEC
    return fromEpochSeconds(secs, ns.toInt())
}
