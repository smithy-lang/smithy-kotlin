/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.operation.ExecutionContext

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
    public val ClientName: ClientOption<String> = ClientOption("aws.smithy.kotlin#ClientName")

    /**
     * A token generator for idempotency tokens
     */
    public val IdempotencyTokenProvider: ClientOption<IdempotencyTokenProvider> = ClientOption("IdempotencyTokenProvider")

    /**
     * The client logging mode (see [LogMode]
     */
    public val LogMode: ClientOption<LogMode> = ClientOption("LogMode")

    /**
     * Whether endpoint discovery is enabled or not. Default is true
     */
    public val EndpointDiscoveryEnabled: ClientOption<Boolean> = ClientOption("EndpointDiscoveryEnabled")
}

/**
 * Get the [IdempotencyTokenProvider] from the context. If one is not set the default will be returned.
 */
@InternalApi
public val ExecutionContext.idempotencyTokenProvider: IdempotencyTokenProvider
    get() = getOrNull(SdkClientOption.IdempotencyTokenProvider) ?: IdempotencyTokenProvider.Default

/**
 * Get the [LogMode] from the context. If one is not set a default will be returned
 */
@InternalApi
public val ExecutionContext.logMode: LogMode
    get() = getOrNull(SdkClientOption.LogMode) ?: LogMode.Default

/**
 * Get the name of the operation from the context.
 */
@InternalApi
public val ExecutionContext.operationName: String?
    get() = getOrNull(SdkClientOption.OperationName)
