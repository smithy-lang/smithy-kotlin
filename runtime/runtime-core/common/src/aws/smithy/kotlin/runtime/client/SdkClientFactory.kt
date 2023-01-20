/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

/**
 * Interface all generated [SdkClient] companion objects inherit from.
 */
public interface SdkClientFactory<
    TConfig : SdkClientConfig,
    TConfigBuilder : SdkClientConfig.Builder<TConfig>,
    TClient : SdkClient,
    out TClientBuilder : SdkClient.Builder<TConfig, TConfigBuilder, TClient>,
    > {
    /**
     * Return a [TClientBuilder] that can create a new [TClient] instance
     */
    public fun builder(): TClientBuilder

    /**
     * Configure a new [TClient] with [block].
     *
     * Example
     * ```
     * val client = FooClient { ... }
     * ```
     */
    public operator fun invoke(block: TConfigBuilder.() -> Unit): TClient = builder().apply {
        config.apply(block)
    }.build()
}
