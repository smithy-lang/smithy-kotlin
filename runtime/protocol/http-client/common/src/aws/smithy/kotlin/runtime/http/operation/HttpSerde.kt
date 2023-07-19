/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Implemented by types that know how to serialize to the HTTP protocol.
 */
@InternalApi
public fun interface HttpSerialize<T> {
    public suspend fun serialize(context: ExecutionContext, input: T): HttpRequestBuilder
}

/**
 * Implemented by types that know how to deserialize from the HTTP protocol.
 */
@InternalApi
public fun interface HttpDeserialize<T> {
    public suspend fun deserialize(context: ExecutionContext, response: HttpResponse): T
}

/**
 * Convenience deserialize implementation for a type with no output type
 */
@InternalApi
public object UnitDeserializer : HttpDeserialize<Unit> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse) {}
}

/**
 * Convenience serialize implementation for a type with no input type
 */
@InternalApi
public object UnitSerializer : HttpSerialize<Unit> {
    override suspend fun serialize(context: ExecutionContext, input: Unit): HttpRequestBuilder = HttpRequestBuilder()
}

/**
 * Convenience deserialize implementation that returns the response without modification
 */
@InternalApi
public object IdentityDeserializer : HttpDeserialize<HttpResponse> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse): HttpResponse = response
}
