/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.config

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.compression.Gzip

@DslMarker
public annotation class CompressionClientConfigDsl

/**
 * The configuration properties for a client that supports compression
 */
public interface CompressionClientConfig {
    public val requestCompression: RequestCompressionConfig

    @CompressionClientConfigDsl
    public interface Builder {
        public var requestCompression: RequestCompressionConfig.Builder

        public fun requestCompression(block: RequestCompressionConfig.Builder.() -> Unit) {
            requestCompression.apply(block)
        }
    }
}

/**
 * The configuration properties for request compression.
 */
public class RequestCompressionConfig(builder: Builder) {
    public companion object {
        public inline operator fun invoke(block: Builder.() -> Unit): RequestCompressionConfig =
            RequestCompressionConfig(Builder().apply(block))
    }

    /**
     * The list of compression algorithms supported by the client.
     * More compression algorithms can be added and may override an existing implementation.
     * Use the `CompressionAlgorithm` interface to create one.
     */
    public val compressionAlgorithms: List<CompressionAlgorithm> = builder.compressionAlgorithms

    /**
     * Flag used to determine when a request should be compressed or not.
     * False by default.
     */
    public val disableRequestCompression: Boolean = builder.disableRequestCompression ?: false

    /**
     * The threshold in bytes used to determine if a request should be compressed or not.
     * MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
     */
    public val requestMinCompressionSizeBytes: Long = builder.requestMinCompressionSizeBytes ?: 10_240

    @InternalApi
    public fun toBuilderApplicator(): Builder = Builder().apply {
        compressionAlgorithms = this@RequestCompressionConfig.compressionAlgorithms.toMutableList()
        disableRequestCompression = this@RequestCompressionConfig.disableRequestCompression
        requestMinCompressionSizeBytes = this@RequestCompressionConfig.requestMinCompressionSizeBytes
    }

    /**
     * A builder for [CompressionClientConfig]
     */
    @CompressionClientConfigDsl
    public class Builder {
        /**
         * The list of compression algorithms supported by the client.
         * More compression algorithms can be added and may override an existing implementation.
         * Use the `CompressionAlgorithm` interface to create one.
         */
        public var compressionAlgorithms: MutableList<CompressionAlgorithm> = mutableListOf(Gzip())

        /**
         * Flag used to determine when a request should be compressed or not.
         * False by default.
         */
        public var disableRequestCompression: Boolean? = null

        /**
         * The threshold in bytes used to determine if a request should be compressed or not.
         * MUST be in the range 0-10,485,760 (10 MB). Defaults to 10,240 (10 KB).
         */
        public var requestMinCompressionSizeBytes: Long? = null
    }
}
