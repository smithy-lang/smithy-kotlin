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
package software.aws.clientrt.http.request
import kotlin.reflect.KClass
import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.TypeInfo

/**
 * A prepared HTTP request for a client to execute. This does nothing until the [execute] method is called.
 */
class PreparedHttpRequest(
    val client: SdkHttpClient,
    val builder: HttpRequestBuilder,
    val input: Any? = null,
    val userContext: Any? = null
) {

    /**
     * Execute this request and return the result of the [SdkHttpClient.responsePipeline]
     * @throws ResponseTransformFailed
     */
    suspend inline fun <reified TResponse> execute(): TResponse {
        val subject: Any = input ?: builder.body
        client.requestPipeline.execute(builder, subject)
        val httpResponse = client.engine.roundTrip(builder)

        val want = TypeInfo(TResponse::class)
        val responseContext = HttpResponseContext(httpResponse, want, userContext = userContext)
        val response = client.responsePipeline.execute(responseContext, httpResponse.body)

        if (response !is TResponse) {
            // response pipeline failed to transform the raw HttResponse content into the expected output type
            throw ResponseTransformFailed(httpResponse, response::class, want.classz)
        }

        return response
    }
}

/**
 * Exception thrown when the response pipeline fails to transform the raw Http response into
 * the expected output type.
 * It includes the received type and the expected type as part of the message.
 */
class ResponseTransformFailed(
    response: HttpResponse,
    from: KClass<*>,
    to: KClass<*>
) : UnsupportedOperationException() {
    override val message: String? = """Response transform failed: $from -> $to
        |with response from ${response.request.url}:
        |status: ${response.status}
        |response headers: 
        |${response.headers.entries().joinToString { (key, values) -> "$key: $values\n" }}
    """.trimMargin()
}
