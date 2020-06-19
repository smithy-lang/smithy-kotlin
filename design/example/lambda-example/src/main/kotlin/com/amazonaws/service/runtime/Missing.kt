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

package com.amazonaws.service.runtime

import software.aws.clientrt.SdkBaseException
import software.aws.clientrt.content.ByteStream
import software.aws.clientrt.http.*
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.io.Source
import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.Serializer
import software.aws.clientrt.serde.json.JsonDeserializer
import software.aws.clientrt.serde.json.JsonSerializer

// ######################################################################################
// Things either missing or in-progress in the client runtime.
// ######################################################################################

interface SdkClient {
    val serviceName: String
}

interface HttpSerialize {
    suspend fun serialize(builder: HttpRequestBuilder, serializer: Serializer)
}

interface HttpDeserialize {
    suspend fun deserialize(response: HttpResponse, deserializer: Deserializer): Any
}

interface SerdeProvider {
    fun serializer(): Serializer
    fun deserializer(payload: ByteArray): Deserializer
}

class JsonSerdeProvider: SerdeProvider {
    override fun serializer(): Serializer = JsonSerializer()
    override fun deserializer(payload: ByteArray): Deserializer = JsonDeserializer(payload)
}

// Http Serialization/Deserialization feature (handles calling the appropriate serialize/deserialize methods)
class HttpSerde(private val serde: SerdeProvider): Feature {
    class Config {
        var serdeProvider: SerdeProvider? = null
    }

    companion object Feature: HttpClientFeatureFactory<Config, HttpSerde> {
        override val key: FeatureKey<HttpSerde> = FeatureKey("HttpSerde")
        override fun create(block: Config.() -> Unit): HttpSerde {
            val config = Config().apply(block)
            // TODO - validate config
            return HttpSerde(config.serdeProvider!!)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) { subject ->
            when(subject) {
                // serialize the input type to the outgoing request builder
                is HttpSerialize -> subject.serialize(context, serde.serializer())
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Transform) { subject ->
            when(context.userContext) {
                is HttpDeserialize -> {
                    val payload = context.response.body.readAll() ?: return@intercept
                    val deserializer = serde.deserializer(payload)
                    val content = (context.userContext as HttpDeserialize).deserialize(context.response, deserializer)
                    proceedWith(content)
                }
            }
        }
    }
}

// Feature that sets request defaults for all requests
class DefaultRequest(private val builder: HttpRequestBuilder): Feature {
    private val defaultHeaders = builder.headers.build()

    companion object Feature: HttpClientFeatureFactory<HttpRequestBuilder, DefaultRequest> {
        override val key: FeatureKey<DefaultRequest> = FeatureKey("DefaultRequest")
        override fun create(block: HttpRequestBuilder.() -> Unit): DefaultRequest {
            val builder = HttpRequestBuilder().apply(block)
            return DefaultRequest(builder)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            context.url.host = builder.url.host
            context.url.port = builder.url.port
            context.url.parameters = builder.url.parameters
            context.url.scheme = builder.url.scheme
            context.url.forceQuery = builder.url.forceQuery
            context.url.fragment = builder.url.fragment
            context.url.userInfo = builder.url.userInfo
            context.headers.appendAll(defaultHeaders)
            context.body = builder.body
        }
    }
}

/**
 * Generic HTTP service exception
 */
class HttpResponseException: SdkBaseException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * The HTTP response status code
     */
    var statusCode: HttpStatusCode? = null

    /**
     * The response headers
     */
    var headers: Headers? = null

    /**
     * The response payload, if available
     */
    var body: ByteArray? = null

    /**
     * The original request
     */
    var request: HttpRequest? = null
}

// Feature that inspects the HTTP response and throws an exception if it is not successfull
// This is provided for clients generated by smithy-kotlin-codegen. Not expected to be used by AWS
// services which define specific mappings from an error to the appropriate modeled exception. Out of the
// box nothing in smithy gives us that ability (other than the HTTP status code which is not guaranteed unique per error)
// so all we can do is throw a generic exception with the code and let the user figure out what modeled error it was
// using whatever matching mechanism they want.
class DefaultValidateResponse: Feature {

    companion object Feature: HttpClientFeatureFactory<DefaultValidateResponse, DefaultValidateResponse> {
        override val key: FeatureKey<DefaultValidateResponse> = FeatureKey("DefaultValidateResponse")
        override fun create(block: DefaultValidateResponse.() -> Unit): DefaultValidateResponse{
            return DefaultValidateResponse().apply(block)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            if (context.response.status.isSuccess()) {
                proceed()
                return@intercept
            }

            val message = "received unsuccessful HTTP response: ${context.response.status}"
            val httpException = HttpResponseException(message).apply {
                statusCode = context.response.status
                headers = context.response.headers
                body = context.response.body.readAll()
                request = context.response.request
            }

            throw httpException
        }
    }
}

fun ByteStream.toHttpBody(): HttpBody {
    val bytestream = this
    return when(bytestream) {
        is ByteStream.Buffer -> object: HttpBody.Bytes() {
            override val contentLength: Long? = bytestream.contentLength
            override fun bytes(): ByteArray = bytestream.bytes()
        }
        is ByteStream.Reader -> object: HttpBody.Streaming() {
            override val contentLength: Long? = bytestream.contentLength
            override fun readFrom(): Source = bytestream.readFrom()
        }
    }
}

suspend fun HttpBody.readAll(): ByteArray? = when(this) {
    is HttpBody.Bytes -> this.bytes()
    is HttpBody.Streaming -> this.readFrom().readAll()
    else -> null
}
