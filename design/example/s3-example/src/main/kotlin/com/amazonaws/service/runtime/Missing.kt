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

import software.aws.clientrt.content.ByteStream
import software.aws.clientrt.http.*
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline
import software.aws.clientrt.io.Source

// ######################################################################################
// Things either missing or in-progress in the client runtime.
// ######################################################################################

interface SdkClient {
    val serviceName: String
    fun close() {}
}

interface Serializer
class JsonSerializer: Serializer

interface HttpSerialize {
    suspend fun serialize(builder: HttpRequestBuilder, serializer: Serializer)
}

interface Deserializer
class JsonDeserializer: Deserializer

interface HttpDeserialize {
    suspend fun deserialize(response: HttpResponse, deserializer: Deserializer): Any
}

// Http Serialization/Deserialization feature (handles calling the appropriate serialize/deserialize methods)
class HttpSerde(private val serializer: Serializer, private val deserializer: Deserializer): Feature {
    class Config {
        // FIXME - this is likely more like "SerdeProvider" since a deserializer is instantiated per payload
        var serializer: Serializer? = null
        var deserializer: Deserializer? = null
    }

    companion object Feature: HttpClientFeatureFactory<Config, HttpSerde> {
        override val key: FeatureKey<HttpSerde> = FeatureKey("HttpSerde")
        override fun create(block: Config.() -> Unit): HttpSerde {
            val config = Config().apply(block)
            // TODO - validate config
            return HttpSerde(config.serializer!!, config.deserializer!!)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) { subject ->
            when(subject) {
                // serialize the input type to the outgoing request builder
                is HttpSerialize -> subject.serialize(context, serializer)
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Transform) { subject ->
            when(context.userContext) {
                is HttpDeserialize -> {
                    val content = (context.userContext as HttpDeserialize).deserialize(context.response, deserializer)
                    proceedWith(content)
                }
            }
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


fun HttpBody.toByteStream(): ByteStream? {
    val body = this
    return when(body) {
        is HttpBody.Bytes -> object: ByteStream.Buffer() {
            override val contentLength: Long? = body.contentLength
            override fun bytes(): ByteArray = body.bytes()
        }
        is HttpBody.Streaming -> object: ByteStream.Reader() {
            override val contentLength: Long? = body.contentLength
            override fun readFrom(): Source = body.readFrom()
        }
        else -> null
    }
}


suspend fun ByteStream.toByteArray(): ByteArray {
    val stream = this
    return when(stream) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().readAll()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun ByteStream.decodeToString(): String = toByteArray().decodeToString()


// TODO - toFile(...)

fun ByteStream.cancel() {
    val stream = this
    when(stream) {
        is ByteStream.Buffer -> stream.bytes()
        is ByteStream.Reader -> stream.readFrom().cancel(null)
    }
}
