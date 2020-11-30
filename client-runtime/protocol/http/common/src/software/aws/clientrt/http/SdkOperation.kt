/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.client.ClientOptionsBuilder
import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.client.SdkClientOption
import software.aws.clientrt.http.feature.HttpDeserialize
import software.aws.clientrt.http.feature.HttpSerialize
import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.InternalAPI

/**
 * Common configuration for an SDK (HTTP) operation/call
 */
@InternalAPI
open class SdkOperation {

    companion object {
        /**
         * The operation serializer (if any) is stored under this key
         */
        val OperationSerializer: AttributeKey<HttpSerialize> = AttributeKey("OperationSerializer")

        /**
         * The operation deserializer (if any) is stored under this key
         */
        val OperationDeserializer: AttributeKey<HttpDeserialize> = AttributeKey("OperationDeserializer")

        /**
         * The expected HTTP status code of a successful response is stored under this key
         */
        val ExpectedHttpStatus: AttributeKey<Int> = AttributeKey("ExpectedHttpStatus")

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
         * The serializer to use for the request
         */
        var serializer: HttpSerialize? by option(OperationSerializer)

        /**
         * The deserializer to use for the response
         */
        var deserializer: HttpDeserialize? by option(OperationDeserializer)

        /**
         * The expected HTTP status code on success
         */
        var expectedHttpStatus: Int? by option(ExpectedHttpStatus)
    }
}
