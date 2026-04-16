/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

public abstract class AbstractSdkClientFactory<
    TConfig : SdkClientConfig,
    TConfigBuilder : SdkClientConfig.Builder<TConfig>,
    TClient : SdkClient,
    TClientBuilder : SdkClient.Builder<TConfig, TConfigBuilder, TClient>,
    > : SdkClientFactory<TConfig, TConfigBuilder, TClient, TClientBuilder> {

    /**
     * Inject any client-specific config
     */
    protected open fun finalizeConfig(builder: TClientBuilder) { }

    public override operator fun invoke(block: TConfigBuilder.() -> Unit): TClient = builder().apply {
        config.apply(block)
        finalizeConfig(this)
    }.build()
}
