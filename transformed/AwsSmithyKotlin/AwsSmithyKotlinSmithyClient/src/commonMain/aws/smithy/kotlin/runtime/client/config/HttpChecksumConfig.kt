/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client.config

/**
 * Client config for HTTP checksums
 */
public interface HttpChecksumConfig {
    /**
     * Configures request checksum calculation
     */
    public val requestChecksumCalculation: RequestHttpChecksumConfig?

    /**
     * Configures response checksum validation
     */
    public val responseChecksumValidation: ResponseHttpChecksumConfig?

    public interface Builder {
        /**
         * Configures request checksum calculation
         */
        public var requestChecksumCalculation: RequestHttpChecksumConfig?

        /**
         * Configures response checksum validation
         */
        public var responseChecksumValidation: ResponseHttpChecksumConfig?
    }
}

/**
 * Configuration options for enabling and managing HTTP request checksums
 */
public enum class RequestHttpChecksumConfig {
    /**
     * SDK will calculate checksums if the service marks them as required or if the service offers optional checksums.
     */
    WHEN_SUPPORTED,

    /**
     * SDK will only calculate checksums if the service marks them as required.
     */
    WHEN_REQUIRED,
}

/**
 * Configuration options for enabling and managing HTTP response checksums
 */
public enum class ResponseHttpChecksumConfig {
    /**
     * SDK will validate checksums if the service marks them as required or if the service offers optional checksums.
     */
    WHEN_SUPPORTED,

    /**
     * SDK will only validate checksums if the service marks them as required.
     */
    WHEN_REQUIRED,
}
