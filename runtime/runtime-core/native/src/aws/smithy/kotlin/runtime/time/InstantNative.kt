/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.time

import kotlin.time.Duration

public actual class Instant : Comparable<Instant> {
    actual override fun compareTo(other: Instant): Int {
        TODO("Not yet implemented")
    }

    public actual val epochSeconds: Long
        get() = TODO("Not yet implemented")
    public actual val nanosecondsOfSecond: Int
        get() = TODO("Not yet implemented")

    /**
     * Encode the [Instant] as a string into the format specified by [TimestampFormat]
     */
    public actual fun format(fmt: TimestampFormat): String {
        TODO("Not yet implemented")
    }

    /**
     * Returns an instant that is the result of adding the specified [duration] to this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be earlier than this Instant.
     */
    public actual operator fun plus(duration: Duration): Instant {
        TODO("Not yet implemented")
    }

    /**
     * Returns an instant that is the result of subtracting the specified [duration] from this instant.
     * NOTE: Duration may be negative in which case the returned Instant will be later than this Instant.
     */
    public actual operator fun minus(duration: Duration): Instant {
        TODO("Not yet implemented")
    }

    /**
     * Returns a duration representing the amount of time between this and [other]. If [other] is before this instant,
     * the resulting duration will be negative.
     * @param other The [Instant] marking the end of the duration
     */
    public actual operator fun minus(other: Instant): Duration {
        TODO("Not yet implemented")
    }

    public actual companion object {
        /**
         * Parse an ISO-8601 formatted string into an [Instant]
         */
        public actual fun fromIso8601(ts: String): Instant {
            TODO("Not yet implemented")
        }

        /**
         * Parse an RFC5322/RFC-822 formatted string into an [Instant]
         */
        public actual fun fromRfc5322(ts: String): Instant {
            TODO("Not yet implemented")
        }

        /**
         * Create an [Instant] from its parts
         */
        public actual fun fromEpochSeconds(seconds: Long, ns: Int): Instant {
            TODO("Not yet implemented")
        }

        /**
         * Parse a string formatted as epoch-seconds into an [Instant]
         */
        public actual fun fromEpochSeconds(ts: String): Instant {
            TODO("Not yet implemented")
        }

        /**
         * Create an [Instant] from the current system time
         */
        public actual fun now(): Instant {
            TODO("Not yet implemented")
        }

        /**
         * Create an [Instant] with the minimum possible value
         */
        public actual val MIN_VALUE: Instant
            get() = TODO("Not yet implemented")

        /**
         * Create an [Instant] with the maximum possible value
         */
        public actual val MAX_VALUE: Instant
            get() = TODO("Not yet implemented")
    }
}
