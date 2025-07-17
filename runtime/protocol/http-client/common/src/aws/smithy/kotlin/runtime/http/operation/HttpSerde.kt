/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext

/**
 * Implemented by types that know how to serialize to the HTTP protocol.
 */
@InternalApi
public sealed interface HttpSerializer<T> {
    @InternalApi
    public companion object {
        public val Unit: HttpSerializer<Unit> = object : NonStreaming<Unit> {
            override fun serialize(context: ExecutionContext, input: Unit): HttpRequestBuilder =
                HttpRequestBuilder()
        }
    }

    /**
     * Serializer for streaming operations that need full control over serialization of the body
     */
    @InternalApi
    public interface Streaming<T> : HttpSerializer<T> {
        public suspend fun serialize(context: ExecutionContext, input: T): HttpRequestBuilder
    }

    /**
     * Serializer for non-streaming (simple) operations that don't need to ever suspend.
     */
    @InternalApi
    public interface NonStreaming<T> : HttpSerializer<T> {
        public fun serialize(context: ExecutionContext, input: T): HttpRequestBuilder
    }
}

/**
 * Implemented by types that know how to deserialize from the HTTP protocol.
 */
@InternalApi
public sealed interface HttpDeserializer<T> {
    @InternalApi
    public companion object {
        public val Identity: HttpDeserializer<HttpResponse> = object : NonStreaming<HttpResponse> {
            override fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?): HttpResponse =
                call.response
        }

        public val Unit: HttpDeserializer<Unit> = object : NonStreaming<Unit> {
            override fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?) { }
        }
    }

    /**
     * Deserializer for streaming operations that need full control over deserialization of the body
     */
    @InternalApi
    public interface Streaming<T> : HttpDeserializer<T> {
        public suspend fun deserialize(context: ExecutionContext, call: HttpCall): T
    }

    /**
     * Deserializer for non-streaming (simple) operations that don't need to ever suspend. These
     * operations are handed the full payload if it exists.
     */
    @InternalApi
    public interface NonStreaming<T> : HttpDeserializer<T> {
        public fun deserialize(context: ExecutionContext, call: HttpCall, payload: ByteArray?): T
    }
}
