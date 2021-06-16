/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

/**
 * Create a new test operation using [serialized] as the already serialized version of the input type [I]
 * and [deserialized] as the result of "deserialization" from an HTTP response.
 */
fun <I, O> newTestOperation(serialized: HttpRequestBuilder, deserialized: O): SdkHttpOperation<I, O> =
    SdkHttpOperation.build<I, O> {
        serializer = object : HttpSerialize<I> {
            override suspend fun serialize(context: ExecutionContext, input: I): HttpRequestBuilder = serialized
        }

        deserializer = object : HttpDeserialize<O> {
            override suspend fun deserialize(context: ExecutionContext, response: HttpResponse): O = deserialized
        }

        context {
            // required operation context
            operationName = "TestOperation"
            service = "TestService"
        }
    }
