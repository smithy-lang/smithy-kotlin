/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder

/**
 * Create a new test operation using [serialized] as the already serialized version of the input type [I]
 * and [deserialized] as the result of "deserialization" from an HTTP response.
 */
inline fun <reified I, reified O> newTestOperation(serialized: HttpRequestBuilder, deserialized: O): SdkHttpOperation<I, O> =
    SdkHttpOperation.build<I, O> {
        serializer = HttpSerialize<I> { _, _ -> serialized }
        deserializer = HttpDeserialize<O> { _, _ -> deserialized }

        // required operation context
        operationName = "TestOperation"
        serviceName = "TestService"
    }

val RetryableServiceTestException = ServiceException("test exception").apply {
    sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true
    sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ServiceException.ErrorType.Server
}
