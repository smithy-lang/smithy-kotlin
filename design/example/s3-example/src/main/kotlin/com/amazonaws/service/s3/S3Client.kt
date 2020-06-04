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

import com.amazonaws.service.runtime.HttpSerde
import com.amazonaws.service.runtime.JsonDeserializer
import com.amazonaws.service.runtime.JsonSerializer
import com.amazonaws.service.runtime.SdkClient
import com.amazonaws.service.s3.model.GetObjectRequest
import com.amazonaws.service.s3.model.GetObjectResponse
import com.amazonaws.service.s3.model.PutObjectRequest
import com.amazonaws.service.s3.model.PutObjectResponse
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.http.Protocol
import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine
import software.aws.clientrt.http.request.HttpRequestPipeline
import software.aws.clientrt.http.sdkHttpClient


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
            context.url.scheme = Protocol.HTTPS
            context.url.host = "8tc2zgzns1.execute-api.us-east-2.amazonaws.com"
        }
    }

    suspend fun putObject(input: PutObjectRequest): PutObjectResponse {
        TODO()
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
        TODO()
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
        TODO()
    }

    interface ResponseTransformer
    suspend fun getObjectAlt3(input: GetObjectRequest, responseTransformer: ResponseTransformer): GetObjectResponse {
        // This is the approach that Java V2 SDK takes. The stream is never actually exposed to the user, instead
        // the user must pass in a "ResponseTransformer" that consumes the response. They provide transforms for
        // writing to file, creating an in memory ByteArray, etc.
        TODO()
    }
}


fun main() = runBlocking{

    val service = S3Client()

    val request = GetObjectRequest {}
}
