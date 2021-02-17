/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

/**
 * Create a new test operation using [serialized] as the already serialized version of the input type [I]
 * and [deserialized] as the result of "deserialization" from an HTTP response.
 */
fun <I, O> newTestOperation(serialized: HttpRequestBuilder, deserialized: O): SdkHttpOperation<I, O> {
    return SdkHttpOperation.build<I, O> {
        serializer = object : HttpSerialize<I> {
            override suspend fun serialize(builder: HttpRequestBuilder, input: I) {
                builder.url.scheme = serialized.url.scheme
                builder.url.host = serialized.url.host
                builder.url.path = serialized.url.path
                builder.url.port = serialized.url.port
                serialized.url.parameters.entries().forEach {
                    builder.url.parameters.appendAll(it.key, it.value)
                }
                builder.url.fragment = serialized.url.fragment
                builder.url.forceQuery = serialized.url.forceQuery

                serialized.headers.entries().forEach {
                    builder.headers.appendAll(it.key, it.value)
                }
                builder.method = serialized.method
                builder.body = serialized.body
            }
        }

        deserializer = object : HttpDeserialize<O> {
            override suspend fun deserialize(response: HttpResponse): O {
                return deserialized
            }
        }

        context {
            // required operation context
            operationName = "TestOperation"
            service = "TestService"
        }
    }
}
