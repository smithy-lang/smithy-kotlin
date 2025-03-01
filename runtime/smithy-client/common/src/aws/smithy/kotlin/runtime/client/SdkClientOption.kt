/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Common client execution options
 */
public object SdkClientOption {

    /**
     * The name of the operation
     */
    public val OperationName: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#OperationName")

    /**
     * The service name the operation is executed against
     */
    public val ServiceName: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#ServiceName")

    /**
     * The unique name given to a service client (often the same as [ServiceName]).
     */
    public val ClientName: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#ClientName")

    /**
     * A token generator for idempotency tokens
     */
    public val IdempotencyTokenProvider: AttributeKey<IdempotencyTokenProvider> = AttributeKey("aws.smithy.kotlin#IdempotencyTokenProvider")

    /**
     * The client logging mode (see [LogMode])
     */
    public val LogMode: AttributeKey<LogMode> = AttributeKey("aws.smithy.kotlin#LogMode")

    /**
     * Whether endpoint discovery is enabled or not. Default is true
     */
    public val EndpointDiscoveryEnabled: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#EndpointDiscoveryEnabled")
}

/**
 * Get the [IdempotencyTokenProvider] from the context. If one is not set the default will be returned.
 */
public val ExecutionContext.idempotencyTokenProvider: IdempotencyTokenProvider
    get() = getOrNull(SdkClientOption.IdempotencyTokenProvider) ?: IdempotencyTokenProvider.Default

/**
 * Get the [LogMode] from the context. If one is not set a default will be returned
 */
public val ExecutionContext.logMode: LogMode
    get() = getOrNull(SdkClientOption.LogMode) ?: LogMode.Default

/**
 * Get the name of the operation being invoked from the context.
 */
public val ExecutionContext.operationName: String?
    get() = getOrNull(SdkClientOption.OperationName)

/**
 * Get the name of the service being invoked from the context.
 */
public val ExecutionContext.serviceName: String?
    get() = getOrNull(SdkClientOption.ServiceName)
