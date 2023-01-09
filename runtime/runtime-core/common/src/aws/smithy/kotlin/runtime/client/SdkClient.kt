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
    public val serviceName: String

    override fun close() {}

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
