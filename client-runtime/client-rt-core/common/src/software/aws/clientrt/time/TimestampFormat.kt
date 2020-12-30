/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

/**
 * Timestamp formats supported
 */
enum class TimestampFormat {
    /**
     * ISO-8601/RFC5399 timestamp
     *
     * Prefers RFC5399 when formatting
     */
    ISO_8601,

    /**
     * RFC-5322/2822/822 IMF timestamp
     * See: https://tools.ietf.org/html/rfc5322
     */
    RFC_5322,

    /**
     * Unix time. Seconds elapsed since the epoch 00:00:00Z 1 January 1970
     */
    EPOCH_SECONDS
}

/**
 * Format an [Instant] to a [String] in accordance with the [TimestampFormat.EPOCH_SECONDS] format
 */
internal fun Instant.toEpochSecondsString(): String = StringBuilder("$epochSeconds").apply {
    if (nanosecondsOfSecond > 0) {
        append(".")
        val ns = "$nanosecondsOfSecond"
        val leadingZeros = "0".repeat(9 - ns.length)
        append(leadingZeros)
        append(ns.trimEnd('0'))
    }
}.toString()

/**
 * Hard-coded day map to ensure correct data always used when formatting
 * (locale data can be changed by application code)
 */
internal val DAY_OF_WEEK: Map<Long, String> = mapOf(
    1L to "Mon",
    2L to "Tue",
    3L to "Wed",
    4L to "Thu",
    5L to "Fri",
    6L to "Sat",
    7L to "Sun"
)

/**
 * Hard-coded month map to ensure correct data always used when formatting
 * (locale data can be changed by application code)
 */
internal val MON_OF_YEAR: Map<Long, String> = mapOf(
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