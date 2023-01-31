/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Abstract base class all [SdkClient] builders should inherit from
 */
@InternalApi
public abstract class AbstractSdkClientBuilder<
    TConfig : SdkClientConfig,
    TConfigBuilder : SdkClientConfig.Builder<TConfig>,
    out TClient : SdkClient,
    > : SdkClient.Builder<TConfig, TConfigBuilder, TClient> {

    final override fun build(): TClient = newClient(config.build())

    /**
     * Return a new [TClient] instance with the given [config]
     */
    protected abstract fun newClient(config: TConfig): TClient
}
