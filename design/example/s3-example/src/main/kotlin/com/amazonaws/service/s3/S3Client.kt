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
package com.amazonaws.service.s3

import com.amazonaws.service.runtime.*
import com.amazonaws.service.s3.model.GetObjectRequest
import com.amazonaws.service.s3.model.GetObjectResponse
import com.amazonaws.service.s3.model.PutObjectRequest
import com.amazonaws.service.s3.model.PutObjectResponse
import com.amazonaws.service.s3.transform.GetObjectRequestSerializer
import com.amazonaws.service.s3.transform.GetObjectResponseDeserializer
import com.amazonaws.service.s3.transform.PutObjectRequestSerializer
import com.amazonaws.service.s3.transform.PutObjectResponseDeserializer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.content.ByteStream
import software.aws.clientrt.http.Protocol
import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.roundTrip
import software.aws.clientrt.http.sdkHttpClient
import kotlin.text.decodeToString


class S3Client: SdkClient {
    override val serviceName: String = "s3"
    private val client: SdkHttpClient

    init {
        val config = HttpClientEngineConfig()
        client = sdkHttpClient(KtorEngine(config)) {
            install(HttpSerde) {
                // obviously S3 is actually XML
                serializer = JsonSerializer()
                deserializer = JsonDeserializer()
            }
        }
        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            context.url.scheme = Protocol.HTTP
            context.url.host = "127.0.0.1"
            context.url.port = 8000
        }
    }

    suspend fun putObject(input: PutObjectRequest): PutObjectResponse {
        return client.roundTrip(PutObjectRequestSerializer(input), PutObjectResponseDeserializer())
    }

    suspend fun getObjectAlt1(input: GetObjectRequest, block: suspend (GetObjectResponse) -> Unit) {
        // The advantage of this approach is clear lifetime of when the stream will be closed.
        // This is what Ktor is moving to, but to be fair their use case is a bit lower level.
        //
        // The problem with this approach is it will conflict with a DSL style builder we have discussed
        // implementing:
        //
        // e.g.
        // suspend fun getObject(block: GetObjectRequest.DslBuilder.() -> Unit): GetObjectResponse
        //
        // where the user can call it and construct the request inline
        //
        // e.g.
        //
        // val resp = service.getObject() {
        //     field1 = "blah"
        //     field2 = "blerg"
        // }
        val response: GetObjectResponse = client.roundTrip(GetObjectRequestSerializer(input), GetObjectResponseDeserializer())
        try {
            block(response)
        } finally {
            // perform cleanup / release network resources
            response.body?.cancel()
        }
    }

    suspend fun getObjectAlt2(input: GetObjectRequest): GetObjectResponse {
        // The problem with this approach is knowing when the stream is consumed and resources can
        // be cleaned up. Of course the most likely use cases (e.g. writing to file, or conversion to in memory
        // ByteArray) can be implemented as extensions on the body type and close the stream for the user.
        //
        // That just leaves if the user decides to not use one of those methods and forgets to close it.
        // The async byte stream type (whatever it is) would implement `Closeable` so the user *should* either
        // only use one of the extension methods OR consume the response with a "use" extension function
        //
        // e.g.
        // resp.body.use { ... }
        //
        // There will always be the (small) chance of misuse with this interface. However, I think this is my
        // personal preference to implement. It will make the entire API feel uniform vs having stream responses
        // stick out as distinct.
        return client.roundTrip(GetObjectRequestSerializer(input), GetObjectResponseDeserializer())
    }

    interface ResponseTransformer
    suspend fun getObjectAlt3(input: GetObjectRequest, responseTransformer: ResponseTransformer): GetObjectResponse {
        // This is the approach that Java V2 SDK takes. The stream is never actually exposed to the user, instead
        // the user must pass in a "ResponseTransformer" that consumes the response. They provide transforms for
        // writing to file, creating an in memory ByteArray, etc.
        TODO()
    }
}


@OptIn(ExperimentalStdlibApi::class)
fun main() = runBlocking{

    val service = S3Client()
    val putRequest = PutObjectRequest{
        body = ByteStream.fromString("my bucket content") 
        bucket = "my-bucket"
        key = "config.txt"
        contentType = "application/text"
    }

    val putObjResp = service.putObject(putRequest)
    println("PutObjectResponse")
    println(putObjResp)

    val getRequest = GetObjectRequest {
        bucket = "my-bucket"
        key = "lorem-ipsum"
    }
    println("\n\n")
    println("GetObjectRequest::Alternative 1")
    service.getObjectAlt1(getRequest) {
        // do whatever you need to do with resp / body
        val bytes = it.body?.toByteArray()
        println("content length: ${bytes?.size}")
    }  // the response will no longer be valid at the end of this block though

    println("\n\n")
    println("GetObjectRequest::Alternative 2")

    val getObjResp = service.getObjectAlt2(getRequest)
    println("GetObjectResponse")

    println("""
    Content-Length: ${getObjResp.contentLength}
    Content-Type: ${getObjResp.contentType}
    Version-ID: ${getObjResp.versionId}
    ...
    """.trimIndent())

    println("body:")
    println(getObjResp.body?.decodeToString())


    // example of reading the response body as a stream (without going through one of the
    // provided transforms e.g. decodeToString(), toByteArray(), toFile(), etc)
    val getObjResp2 = service.getObjectAlt2(getRequest)
    getObjResp2.body?.let { body ->
        val stream = body as ByteStream.Reader
        val source = stream.readFrom()
        // read (up to) 64 bytes at a time
        val buffer = ByteArray(64)
        var bytesRead = 0

        while(!source.isClosedForRead) {
            val read = source.readAvailable(buffer, 0, buffer.size)
            val contents = buffer.decodeToString()
            println("read: $contents")
            if (read > 0) bytesRead += read
        }
        println("read total of $bytesRead bytes")
    }

    println("exiting main")
}
