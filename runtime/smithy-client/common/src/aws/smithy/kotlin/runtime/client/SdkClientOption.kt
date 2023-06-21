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
    public val OperationName: ClientOption<String> = ClientOption("aws.smithy.kotlin#OperationName")

    /**
     * The service name the operation is executed against
     */
    public val ServiceName: ClientOption<String> = ClientOption("aws.smithy.kotlin#ServiceName")

    /**
     * The unique name given to a service client (often the same as [ServiceName]).
     */
    public val ClientName: ClientOption<String> = ClientOption("aws.smithy.kotlin#ClientName")

    /**
     * A token generator for idempotency tokens
     */
    public val IdempotencyTokenProvider: ClientOption<IdempotencyTokenProvider> = ClientOption("aws.smithy.kotlin#IdempotencyTokenProvider")

    /**
     * The client logging mode (see [LogMode])
     */
    public val LogMode: ClientOption<LogMode> = ClientOption("aws.smithy.kotlin#LogMode")

    /**
     * Whether endpoint discovery is enabled or not. Default is true
     */
    public val EndpointDiscoveryEnabled: ClientOption<Boolean> = ClientOption("aws.smithy.kotlin#EndpointDiscoveryEnabled")
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
 * Get the name of the operation being invoked from the context.
 */
@InternalApi
public val ExecutionContext.operationName: String?
    get() = getOrNull(SdkClientOption.OperationName)

/**
 * Get the name of the service being invoked from the context.
 */
@InternalApi
public val ExecutionContext.serviceName: String?
    get() = getOrNull(SdkClientOption.ServiceName)
