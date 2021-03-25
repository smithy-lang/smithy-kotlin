/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ClientOptionsBuilder
import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.client.SdkClientOption
import software.aws.clientrt.http.response.HttpCall
import software.aws.clientrt.logging.Logger
import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.InternalApi
import software.aws.clientrt.util.get

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
         * The logging instance for a single operation. This is guaranteed to exist.
         */
        val Logger: AttributeKey<Logger> = AttributeKey("Logger")

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

/**
 * Get the operation logger from the context
 */
val ExecutionContext.logger: Logger
    get() = this[HttpOperationContext.Logger]
