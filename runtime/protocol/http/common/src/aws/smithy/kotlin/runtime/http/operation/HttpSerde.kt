/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse

// FIXME - if SAM interfaces support suspend soon we should consider updating HttpSerialize/Deserialize to use them instead
// see: https://youtrack.jetbrains.com/issue/KT-40978

/**
 * Implemented by types that know how to serialize to the HTTP protocol.
 */
public interface HttpSerialize<T> {
    public suspend fun serialize(context: ExecutionContext, input: T): HttpRequestBuilder
}

/**
 * Implemented by types that know how to deserialize from the HTTP protocol.
 */
public interface HttpDeserialize<T> {
    public suspend fun deserialize(context: ExecutionContext, response: HttpResponse): T
}

/**
 * Convenience deserialize implementation for a type with no output type
 */
public object UnitDeserializer : HttpDeserialize<Unit> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse) {}
}

/**
 * Convenience serialize implementation for a type with no input type
 */
public object UnitSerializer : HttpSerialize<Unit> {
    override suspend fun serialize(context: ExecutionContext, input: Unit): HttpRequestBuilder = HttpRequestBuilder()
}

/**
 * Convenience deserialize implementation that returns the response without modification
 */
public object IdentityDeserializer : HttpDeserialize<HttpResponse> {
    override suspend fun deserialize(context: ExecutionContext, response: HttpResponse): HttpResponse = response
}
