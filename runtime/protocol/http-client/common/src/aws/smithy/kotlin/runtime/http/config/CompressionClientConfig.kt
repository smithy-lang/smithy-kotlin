/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.config

/**
 * The user-accessible configuration properties for configuring request compression.
 */
public interface CompressionClientConfig {
    /**
     * Flag used to determine when a request should be compressed or not.
     * False by default.
     */
    public val disableRequestCompression: Boolean

    /**
     * The threshold in bytes used to determine if a request should be compressed or not.
     * MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
     */
    public val requestMinCompressionSizeBytes: Long

    public interface Builder {
        /**
         * Flag used to determine when a request should be compressed or not.
         * False by default.
         */
        public var disableRequestCompression: Boolean?

        /**
         * The threshold in bytes used to determine if a request should be compressed or not.
         * MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
         */
        public var requestMinCompressionSizeBytes: Long?
    }
}
