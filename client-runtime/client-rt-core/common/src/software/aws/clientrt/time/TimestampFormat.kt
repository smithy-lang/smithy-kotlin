/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
