/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.http.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.compression.Gzip

@DslMarker
public annotation class CompressionClientConfigDsl

public interface CompressionClientConfig {
    public val requestCompression: RequestCompressionConfig

    public interface Builder {
        public fun requestCompression(block: RequestCompressionConfig.Builder?.() -> Unit) {
            requestCompression.apply(block)
        }

        public var requestCompression: RequestCompressionConfig.Builder?
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

    public fun toBuilderApplicator(): Builder = Builder().apply {
        compressionAlgorithms = this@RequestCompressionConfig.compressionAlgorithms.toMutableList()
        disableRequestCompression = this@RequestCompressionConfig.disableRequestCompression
        requestMinCompressionSizeBytes = this@RequestCompressionConfig.requestMinCompressionSizeBytes
    }

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

    /**
     * A builder for [CompressionClientConfig]
     */
    @CompressionClientConfigDsl
    public class Builder {
        /**
         * The list of compression algorithms supported by the SDK.
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

    init {
        compressionAlgorithms = builder.compressionAlgorithms
        disableRequestCompression = builder.disableRequestCompression ?: false
        requestMinCompressionSizeBytes = builder.requestMinCompressionSizeBytes ?: 10_240
    }
}
