/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

// FIXME - remove in favor of kotlinx-datetime before GA (assuming it's available). For now
// we are stubbing this out for codegen purposes and supporting the various timestamp format parsers.
// the actual `Instant` class has additional methods users would actually want/need.

// nanoseconds/sec
internal const val NS_PER_SEC = 1_000_000_000

// represents a moment on the UTC-SLS time scale
expect class Instant : Comparable<Instant> {
    val epochSeconds: Long
    val nanosecondsOfSecond: Int

    override operator fun compareTo(other: Instant): Int

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    fun format(fmt: TimestampFormat): String

    companion object {
        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        fun fromIso8601(ts: String): Instant

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        fun fromRfc5322(ts: String): Instant

        /**
         * Create an [Instant] from its parts
         */
        fun fromEpochSeconds(seconds: Long, ns: Int): Instant

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        fun fromEpochSeconds(ts: String): Instant
    }
}

/**
 * Convert [Instant] to a double representing seconds and milliseconds since the epoch
 */
fun Instant.toEpochDouble(): Double = epochSeconds.toDouble() + (nanosecondsOfSecond.toDouble() / NS_PER_SEC)
