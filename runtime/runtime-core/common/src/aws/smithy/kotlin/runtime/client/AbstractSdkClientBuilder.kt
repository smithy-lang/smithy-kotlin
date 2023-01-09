/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Abstract base class all [SdkClient] builders should inherit from
 */
@InternalApi
public abstract class AbstractSdkClientBuilder<
    TConfig : SdkClientConfig,
    TConfigBuilder : SdkClientConfig.Builder<TConfig>,
    out TClient : SdkClient,
    > : SdkClient.Builder<TConfig, TConfigBuilder, TClient> {

    final override fun build(): TClient {
        finalizeConfig()
        return newClient(config.build())
    }

    /**
     * Hook for subclasses to finalize any configuration values before build is called on the config builder.
     */
    protected open fun finalizeConfig() { }

    /**
     * Return a new [TClient] instance with the given [config]
     */
    public abstract fun newClient(config: TConfig): TClient
}
