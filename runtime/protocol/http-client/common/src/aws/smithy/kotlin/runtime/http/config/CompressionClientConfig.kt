/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.http.compression.CompressionAlgorithm

/**
 * The user-accessible configuration properties for configuring request compression.
 */
public interface CompressionClientConfig {
    /**
     * The list of compression algorithms supported by the SDK.
     * More compression algorithms can be added and may override an existing implementation.
     * Use the `CompressionAlgorithm` interface to create one.
     */
    public val compressionAlgorithms: List<CompressionAlgorithm>

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
         * The list of compression algorithms supported by the SDK.
         * More compression algorithms can be added and may override an existing implementation.
         * Use the `CompressionAlgorithm` interface to create one.
         */
        public var compressionAlgorithms: List<CompressionAlgorithm>

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
