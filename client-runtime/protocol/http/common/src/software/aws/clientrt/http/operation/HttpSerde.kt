/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

// FIXME - if SAM interfaces support suspend soon we should consider updating HttpSerialize/Deserialize to use them instead
// see: https://youtrack.jetbrains.com/issue/KT-40978

/**
 * Implemented by types that know how to serialize to the HTTP protocol.
 */
interface HttpSerialize<T> {
    suspend fun serialize(context: ExecutionContext, input: T): HttpRequestBuilder
}

/**
 * Implemented by types that know how to deserialize from the HTTP protocol.
 */
interface HttpDeserialize<T> {
    suspend fun deserialize(context: ExecutionContext, response: HttpResponse): T
}

/**
 * Convenience deserialize implementation for a type with no output type
 */
object UnitDeserializer : HttpDeserialize<Unit> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse) {}
}

/**
 * Convenience serialize implementation for a type with no input type
 */
object UnitSerializer : HttpSerialize<Unit> {
    override suspend fun serialize(context: ExecutionContext, input: Unit): HttpRequestBuilder = HttpRequestBuilder()
}

/**
 * Convenience deserialize implementation that returns the response without modification
 */
object IdentityDeserializer : HttpDeserialize<HttpResponse> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse): HttpResponse {
        return response
    }
}
