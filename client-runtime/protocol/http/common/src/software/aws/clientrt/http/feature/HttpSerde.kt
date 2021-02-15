/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

/**
 * Implemented by types that know how to serialize to the HTTP protocol.
 */
interface HttpSerialize<T> {
    suspend fun serialize(builder: HttpRequestBuilder, input: T)
}

/**
 * Implemented by types that know how to deserialize from the HTTP protocol.
 */
interface HttpDeserialize<T> {
    suspend fun deserialize(response: HttpResponse): T
}

/**
 * Convenience deserialize implementation for a type with no output type
 */
object UnitDeserializer : HttpDeserialize<Unit> {
    override suspend fun deserialize(response: HttpResponse) {}
}

/**
 * Convenience serialize implementation for a type with no input type
 */
object UnitSerializer : HttpSerialize<Unit> {
    override suspend fun serialize(builder: HttpRequestBuilder, input: Unit) {}
}
