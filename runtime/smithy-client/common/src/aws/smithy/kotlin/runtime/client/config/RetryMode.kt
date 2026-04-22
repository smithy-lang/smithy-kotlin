/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.config

import aws.smithy.kotlin.runtime.InternalApi

/**
 * The retry mode to be used for the client's retry strategy.
 */
@InternalApi
public enum class RetryMode {
    /**
     * The legacy retry mode preserves the old retry behavior.
     * With this, the client will use the
     * [LegacyRetryStrategy][aws.smithy.kotlin.runtime.retries.LegacyRetryStrategy].
     */
    LEGACY,

    /**
     * The standard retry mode. With this, the client will use the
     * [StandardRetryStrategy][aws.smithy.kotlin.runtime.retries.StandardRetryStrategy].
     *
     * This is the recommended retry mode for the majority of use cases.
     */
    STANDARD,

    /**
     * Adaptive retry mode. With this, the client will use the
     * [AdaptiveRetryStrategy][aws.smithy.kotlin.runtime.retries.AdaptiveRetryStrategy].
     *
     * **Note**: The adaptive retry strategy is an advanced mode. It is not recommended for typical use cases. In most
     * cases, [STANDARD] is the preferred retry mode.
     */
    ADAPTIVE,
}
