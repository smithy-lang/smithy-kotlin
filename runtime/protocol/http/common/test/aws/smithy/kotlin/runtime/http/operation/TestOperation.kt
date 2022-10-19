/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse

/**
 * Create a new test operation using [serialized] as the already serialized version of the input type [I]
 * and [deserialized] as the result of "deserialization" from an HTTP response.
 */
fun <I, O> newTestOperation(serialized: HttpRequestBuilder, deserialized: O): SdkHttpOperation<I, O> =
    SdkHttpOperation.build {
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
