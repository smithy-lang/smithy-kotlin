/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.time

/**
 * Timestamp formats supported
 */
public enum class TimestampFormat {
    /**
     * ISO-8601/RFC5399 timestamp including fractional seconds at microsecond precision (e.g.,
     * "2022-04-25T16:44:13.667307Z")
     *
     * Prefers RFC5399 when formatting
     */
    ISO_8601,

    /**
     * A condensed ISO-8601 date/time format at second-level precision (e.g., "20220425T164413Z")
     */
    ISO_8601_CONDENSED,

    /**
     * A condensed ISO-8601 date format at day-level precision (e.g., "20220425"). Note that this format is always in
     * UTC despite not including an offset identifier in the output.
     */
    ISO_8601_CONDENSED_DATE,

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
