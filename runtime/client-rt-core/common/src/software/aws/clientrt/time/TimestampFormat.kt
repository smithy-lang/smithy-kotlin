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
