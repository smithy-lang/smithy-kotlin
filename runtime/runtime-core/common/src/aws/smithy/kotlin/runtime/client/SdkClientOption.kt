/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.config.IdempotencyTokenProvider
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Common client execution options
 */
public object SdkClientOption {

    /**
     * The name of the operation
     */
    public val OperationName: ClientOption<String> = ClientOption("OperationName")

    /**
     * The service name the operation is executed against
     */
    public val ServiceName: ClientOption<String> = ClientOption("ServiceName")

    /**
     * A token generator for idempotency tokens
     */
    public val IdempotencyTokenProvider: ClientOption<IdempotencyTokenProvider> = ClientOption("IdempotencyTokenProvider")

    /**
     * The client logging mode (see [SdkLogMode]
     */
    public val LogMode: ClientOption<SdkLogMode> = ClientOption("LogMode")

    /**
     * The source of time for an operation
     */
    public val Clock: ClientOption<Clock> = ClientOption("Clock")
}

/**
 * Get the [IdempotencyTokenProvider] from the context. If one is not set the default will be returned.
 */
@InternalApi
val ExecutionContext.idempotencyTokenProvider: IdempotencyTokenProvider
    get() = getOrNull(SdkClientOption.IdempotencyTokenProvider) ?: IdempotencyTokenProvider.Default

/**
 * Get the [SdkLogMode] from the context. If one is not set a default will be returned
 */
@InternalApi
val ExecutionContext.sdkLogMode: SdkLogMode
    get() = getOrNull(SdkClientOption.LogMode) ?: SdkLogMode.Default
