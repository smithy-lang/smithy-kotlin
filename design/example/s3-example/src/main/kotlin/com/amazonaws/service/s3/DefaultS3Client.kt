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

import com.amazonaws.service.s3.model.*
import com.amazonaws.service.s3.transform.*
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.content.ByteStream
import software.aws.clientrt.content.toByteArray
import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine
import software.aws.clientrt.http.feature.DefaultRequest
import software.aws.clientrt.http.feature.HttpSerde
import software.aws.clientrt.serde.xml.XmlSerdeProvider
import kotlin.text.decodeToString


class DefaultS3Client: S3Client {
    private val client: SdkHttpClient

    init {
        val config = HttpClientEngineConfig()
        client = sdkHttpClient(KtorEngine(config)) {
            install(HttpSerde) {
                // obviously S3 is actually XML
                serdeProvider = XmlSerdeProvider()
            }

            install(DefaultRequest) {
                url.scheme = Protocol.HTTP
                url.host = "127.0.0.1"
                url.port = 8000
            }
        }
    }

    override suspend fun <T> getObject(input: GetObjectRequest, block: suspend (GetObjectResponse) -> T): T {
        return client.execute(GetObjectRequestSerializer(input), GetObjectResponseDeserializer(), block)
        // val response: PreparedHttpRequest = client.roundTrip(GetObjectRequestSerializer(input), GetObjectResponseDeserializer())
        // return response.execute<GetObjectResponse, T>(block)

        // try {
        //     return block(response)
        // } finally {
        //     // FIXME - so we probably want SdkHttpClient/PreparedHttpRequest to return us both TResponse and a response context that can be used to close the underlying response.
        //     // we don't want to be figuring out which field of the response object is the one that needs canceled which is just a proxy
        //     // to the actual resources, we should get back a handle to the resources
        //     response.body?.cancel()
        // }
    }

    override suspend fun getBucketTagging(input: GetBucketTaggingRequest): GetBucketTaggingResponse {
        return client.roundTrip(GetBucketTaggingRequestSerializer(input), GetBucketTaggingResponseDeserializer())
    }

    override suspend fun putObject(input: PutObjectRequest): PutObjectResponse {
        return client.roundTrip(PutObjectRequestSerializer(input), PutObjectResponseDeserializer())
    }

    override fun close() {
        client.close()
    }


}


@OptIn(ExperimentalStdlibApi::class)
fun main() = runBlocking{

    val service = S3Client.create()
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
    println("GetObjectRequest")
    val result = service.getObject(getRequest) { resp ->
        // do whatever you need to do with resp / body
        val bytes = resp.body?.toByteArray()
        println("content length: ${bytes?.size}")
        return@getObject bytes
    }  // the response will no longer be valid at the end of this block though
    println(result)


    // example of dynamically consuming the stream
    service.getObject(getRequest) { resp ->
        resp.body?.let { body ->
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
    }

    // example of XML serde
    val taggingRequest = GetBucketTaggingRequest {
        bucket = "my-bucket"
    }
    val taggingResponse = service.getBucketTagging(taggingRequest)
    println(taggingResponse)

    val taggingRequest2 = GetBucketTaggingRequest {
        bucket = "another-bucket"
    }

    val taggingResponse2 = service.getBucketTagging(taggingRequest2)
    println(taggingResponse2)

    // TODO: Handle error case.  This should return some error that means 404
    /*
    val taggingRequest3 = GetBucketTaggingRequest {
        bucket = "invalid-bucket"
    }

    val taggingResponse3 = service.getBucketTagging(taggingRequest3)
    println(taggingResponse3)
     */

    println("exiting main")
}
