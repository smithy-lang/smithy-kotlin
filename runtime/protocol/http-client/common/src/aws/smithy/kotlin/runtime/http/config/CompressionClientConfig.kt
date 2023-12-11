/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.compression.Gzip

@DslMarker
public annotation class CompressionClientConfigDsl

/**
 * The user-accessible configuration properties for configuring request compression.
 */
public interface CompressionClientConfig {
    public companion object {
        /**
         * Initializes a new [CompressionClientConfig] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: Builder.() -> Unit): CompressionClientConfig =
            CompressionClientConfigImpl(Builder().apply(block))
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

    @InternalApi
    public fun toBuilderApplicator(): Builder.() -> Unit

    /**
     * A builder for [CompressionClientConfig]
     */
    @CompressionClientConfigDsl
    public interface Builder {
        public companion object {
            /**
             * Creates a new, empty builder for an [CompressionClientConfig]
             */
            public operator fun invoke(): Builder = CompressionClientConfigImpl.BuilderImpl()
        }

        /**
         * The list of compression algorithms supported by the SDK.
         * More compression algorithms can be added and may override an existing implementation.
         * Use the `CompressionAlgorithm` interface to create one.
         */
        public var compressionAlgorithms: MutableList<CompressionAlgorithm>

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

@InternalApi
public open class CompressionClientConfigImpl(builder: CompressionClientConfig.Builder) : CompressionClientConfig {
    @InternalApi
    public constructor() : this(BuilderImpl())

    override val compressionAlgorithms: List<CompressionAlgorithm> = builder.compressionAlgorithms
    override val disableRequestCompression: Boolean = builder.disableRequestCompression ?: false
    override val requestMinCompressionSizeBytes: Long = builder.requestMinCompressionSizeBytes ?: 10_240

    override fun toBuilderApplicator(): CompressionClientConfig.Builder.() -> Unit = {
        compressionAlgorithms = this@CompressionClientConfigImpl.compressionAlgorithms.toMutableList()
        disableRequestCompression = this@CompressionClientConfigImpl.disableRequestCompression
        requestMinCompressionSizeBytes = this@CompressionClientConfigImpl.requestMinCompressionSizeBytes
    }

    @InternalApi
    public open class BuilderImpl : CompressionClientConfig.Builder {
        override var compressionAlgorithms: MutableList<CompressionAlgorithm> = mutableListOf(Gzip())
        override var disableRequestCompression: Boolean? = null
        override var requestMinCompressionSizeBytes: Long? = null
    }
}
