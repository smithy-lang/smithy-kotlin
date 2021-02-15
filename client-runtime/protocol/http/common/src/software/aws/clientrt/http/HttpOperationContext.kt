/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.client.ClientOptionsBuilder
import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.client.SdkClientOption
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.InternalAPI

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalAPI
open class HttpOperationContext {

    companion object {
        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("ExpectedHttpStatus")

        /**
         * Raw HTTP response
         */
        val HttpResponse: AttributeKey<HttpResponse> = AttributeKey("HttpResponse")

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
    }
}
