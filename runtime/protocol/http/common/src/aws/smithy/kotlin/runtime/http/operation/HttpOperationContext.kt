/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ClientOptionsBuilder
import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.tracing.logger
import aws.smithy.kotlin.runtime.tracing.traceSpan
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalApi
public open class HttpOperationContext {

    public companion object {
        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        public val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("ExpectedHttpStatus")

        /**
         * A prefix to prepend the resolved hostname with.
         * See [endpointTrait](https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#endpoint-trait)
         */
        public val HostPrefix: AttributeKey<String> = AttributeKey("HostPrefix")

        /**
         * The HTTP calls made for this operation (this may be > 1 if for example retries are involved)
         */
        public val HttpCallList: AttributeKey<List<HttpCall>> = AttributeKey("HttpCallList")

        /**
         * The unique request ID generated for tracking the request in-flight client side.
         *
         * NOTE: This is guaranteed to exist.
         */
        public val SdkRequestId: AttributeKey<String> = AttributeKey("SdkRequestId")

        /**
         * Build this operation into an HTTP [ExecutionContext]
         */
        public fun build(block: Builder.() -> Unit): ExecutionContext = Builder().apply(block).build()
    }

    /**
     * Convenience builder for constructing HTTP client operations
     */
    public open class Builder : ClientOptionsBuilder() {

        /**
         * The service name
         */
        public var service: String? by requiredOption(SdkClientOption.ServiceName)

        /**
         * The name of the operation
         */
        public var operationName: String? by requiredOption(SdkClientOption.OperationName)

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

@InternalApi
public fun ExecutionContext.getLogger(forComponentName: String): Logger = traceSpan.logger(forComponentName)

@InternalApi
public inline fun <reified T> ExecutionContext.getLogger(): Logger =
    getLogger(requireNotNull(T::class.qualifiedName) { "getLogger<T> cannot be used on an anonymous object" })
