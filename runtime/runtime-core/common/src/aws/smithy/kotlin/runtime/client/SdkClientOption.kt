/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

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
}

/**
 * Get the [IdempotencyTokenProvider] from the context. If one is not set the default will be returned.
 */
@InternalApi
public val ExecutionContext.idempotencyTokenProvider: IdempotencyTokenProvider
    get() = getOrNull(SdkClientOption.IdempotencyTokenProvider) ?: IdempotencyTokenProvider.Default

/**
 * Get the [SdkLogMode] from the context. If one is not set a default will be returned
 */
@InternalApi
public val ExecutionContext.sdkLogMode: SdkLogMode
    get() = getOrNull(SdkClientOption.LogMode) ?: SdkLogMode.Default

/**
 * Get the [OperationName] from the context.
 */
@InternalApi
public val ExecutionContext.operationName: String?
    get() = getOrNull(SdkClientOption.OperationName)
