/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.*
import kotlin.time.Duration

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalApi
public object HttpOperationContext {

    /**
     * A prefix to prepend the resolved hostname with.
     * See [endpointTrait](https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#endpoint-trait)
     */
    public val HostPrefix: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#HostPrefix")

    /**
     * The HTTP calls made for this operation (this may be > 1 if for example retries are involved)
     */
    public val HttpCallList: AttributeKey<List<HttpCall>> = AttributeKey("aws.smithy.kotlin#HttpCallList")

    /**
     * The unique request ID generated for tracking the request in-flight client side.
     *
     * NOTE: This is guaranteed to exist.
     */
    public val SdkInvocationId: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#SdkInvocationId")

    /**
     * The operation input pre-serialization.
     *
     * NOTE: This is guaranteed to exist after serialization.
     */
    public val OperationInput: AttributeKey<Any> = AttributeKey("aws.smithy.kotlin#OperationInput")

    /**
     * The operation metrics container used by various components to record metrics
     */
    public val OperationMetrics: AttributeKey<OperationMetrics> = AttributeKey("aws.smithy.kotlin#OperationMetrics")

    /**
     * Cached attribute level attributes (e.g. rpc.method, rpc.service, etc)
     */
    public val OperationAttributes: AttributeKey<Attributes> = AttributeKey("aws.smithy.kotlin#OperationAttributes")

    /**
     * The clock skew duration to apply to any time-based calculations in the operation (e.g. signing)
     */
    public val ClockSkew: AttributeKey<Duration> = AttributeKey("aws.smithy.kotlin#ClockSkew")
}

internal val ExecutionContext.operationMetrics: OperationMetrics
    get() = getOrNull(HttpOperationContext.OperationMetrics) ?: OperationMetrics.None

internal val ExecutionContext.operationAttributes: Attributes
    get() = getOrNull(HttpOperationContext.OperationAttributes) ?: emptyAttributes()
