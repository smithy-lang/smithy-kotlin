/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ClientOptionsBuilder
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalApi
public open class HttpOperationContext {
    @InternalApi
    public companion object {
        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        public val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("aws.smithy.kotlin#ExpectedHttpStatus")

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
         * Build this operation into an HTTP [ExecutionContext]
         */
        public fun build(block: Builder.() -> Unit): ExecutionContext = Builder().apply(block).build()
    }

    /**
     * Convenience builder for constructing HTTP client operations
     */
    @InternalApi
    public open class Builder : ClientOptionsBuilder() {

        /**
         * The name of the operation
         */
        public var operationName: String? by requiredOption(SdkClientOption.OperationName)

        /**
         * The name of the service the request is sent to
         */
        public var serviceName: String? by requiredOption(SdkClientOption.ServiceName)

        /**
         * The expected HTTP status code on success
         */
        public var expectedHttpStatus: Int? by option(ExpectedHttpStatus)

        /**
         * (Optional) prefix to prepend to a (resolved) hostname
         */
        public var hostPrefix: String? by option(HostPrefix)
    }
}

internal val ExecutionContext.operationMetrics: OperationMetrics
    get() = getOrNull(HttpOperationContext.OperationMetrics) ?: OperationMetrics.None

internal val ExecutionContext.operationAttributes: Attributes
    get() = getOrNull(HttpOperationContext.OperationAttributes) ?: emptyAttributes()
