/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ClientOptionsBuilder
import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.logging.withContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.get

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalApi
open class HttpOperationContext {

    companion object {
        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("ExpectedHttpStatus")

        /**
         * A prefix to prepend the resolved hostname with.
         * See: https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#endpoint-trait
         */
        val HostPrefix: AttributeKey<String> = AttributeKey("HostPrefix")

        /**
         * The HTTP calls made for this operation (this may be > 1 if for example retries are involved)
         */
        val HttpCallList: AttributeKey<List<HttpCall>> = AttributeKey("HttpCallList")

        /**
         * The per/request logging context.
         */
        val LoggingContext: AttributeKey<Map<String, Any>> = AttributeKey("LogContext")

        /**
         * The unique request ID generated for tracking the request in-flight client side.
         *
         * NOTE: This is guaranteed to exist.
         */
        val SdkRequestId: AttributeKey<String> = AttributeKey("SdkRequestId")

        /**
         * Build this operation into an HTTP [ExecutionContext]
         */
        fun build(block: Builder.() -> Unit): ExecutionContext = Builder().apply(block).build()
    }

    /**
     * Convenience builder for constructing HTTP client operations
     */
    open class Builder : ClientOptionsBuilder() {

        /**
         * The service name
         */
        var service: String? by requiredOption(SdkClientOption.ServiceName)

        /**
         * The name of the operation
         */
        var operationName: String? by requiredOption(SdkClientOption.OperationName)

        /**
         * The expected HTTP status code on success
         */
        var expectedHttpStatus: Int? by option(ExpectedHttpStatus)

        /**
         * (Optional) prefix to prepend to a (resolved) hostname
         */
        var hostPrefix: String? by option(HostPrefix)
    }
}

@InternalApi
inline fun <reified T> ExecutionContext.getLogger(): Logger {
    val instance = Logger.getLogger<T>()
    val logCtx = this[HttpOperationContext.LoggingContext]
    return instance.withContext(logCtx)
}

@InternalApi
fun ExecutionContext.getLogger(name: String): Logger {
    val instance = Logger.getLogger(name)
    val logCtx = this[HttpOperationContext.LoggingContext]
    return instance.withContext(logCtx)
}

@InternalApi
fun Logger.withContext(context: ExecutionContext): Logger = withContext(context[HttpOperationContext.LoggingContext])
