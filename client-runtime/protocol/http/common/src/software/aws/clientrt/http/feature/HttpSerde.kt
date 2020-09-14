/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.http.*
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.SerdeProvider
import software.aws.clientrt.serde.Serializer

typealias SerializationProvider = () -> Serializer

/**
 * Implemented by types that know how to serialize to the HTTP protocol. A [Serializer] instance
 * is provided for serializing payload contents.
 */
interface HttpSerialize {
    suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider)
}

typealias DeserializationProvider = (payload: ByteArray) -> Deserializer

/**
 * Implemented by types that know how to deserialize from the HTTP protocol. A factory function is provided
 * that can be used to create a [Deserializer] instance for handling response payloads.
 */
interface HttpDeserialize {
    suspend fun deserialize(response: HttpResponse, provider: DeserializationProvider): Any
}

/**
 * HTTP serialization/deserialization feature (handles calling the appropriate serialize/deserialize methods)
 */
class HttpSerde(private val serde: SerdeProvider) : Feature {
    class Config {
        var serdeProvider: SerdeProvider? = null
    }

    companion object Feature : HttpClientFeatureFactory<Config, HttpSerde> {
        override val key: FeatureKey<HttpSerde> = FeatureKey("HttpSerde")
        override fun create(block: Config.() -> Unit): HttpSerde {
            val config = Config().apply(block)
            requireNotNull(config.serdeProvider) { "a serde provider must be set to use the HttpSerde feature" }
            return HttpSerde(config.serdeProvider!!)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) { subject ->
            when (subject) {
                // serialize the input type to the outgoing request builder
                is HttpSerialize -> subject.serialize(context, serde::serializer)
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            context.executionCtx?.deserializer?.let { deserializer ->
                    // it's possible that the response doesn't expect a serialized payload and can be completely
                    // deserialized from the HTTP protocol response (e.g. headers) OR in the case of streaming
                    // we can't read the body into memory ourselves
                    val content = deserializer.deserialize(context.response, serde::deserializer)
                    proceedWith(content)
            }
        }
    }
}
