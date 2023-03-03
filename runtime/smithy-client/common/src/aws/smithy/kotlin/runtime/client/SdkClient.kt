/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.util.Buildable

/**
 * Common interface all generated service clients implement
 */
public interface SdkClient : Closeable {
    /**
     * The name of the service
     */
    public val serviceName: String

    /**
     * The client's configuration
     */
    public val config: SdkClientConfig

    /**
     * Builder responsible for instantiating new [TClient] instances
     *
     * @param TConfig the type of client config
     * @param TConfigBuilder the builder type responsible for creating instances of [TConfig]
     * @param TClient the type of client created by this builder
     */
    public interface Builder<
        TConfig : SdkClientConfig,
        TConfigBuilder : SdkClientConfig.Builder<TConfig>,
        out TClient : SdkClient,
        > : Buildable<TClient> {

        /**
         * The configuration builder for this client
         */
        public val config: TConfigBuilder
    }
}
