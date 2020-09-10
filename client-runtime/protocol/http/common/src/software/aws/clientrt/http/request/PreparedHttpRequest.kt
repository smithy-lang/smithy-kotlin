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
import software.aws.clientrt.http.response.ExecutionContext
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.util.InternalAPI

/**
 * A prepared HTTP request for a client to execute. This does nothing until the [execute] or [receive] method is called.
 */
class PreparedHttpRequest(
    val client: SdkHttpClient,
    val builder: HttpRequestBuilder,
    val input: Any? = null,
    val executionCtx: ExecutionContext? = null
) {

    /**
     * Execute this request and return the [HttpResponse] open. It is up to the caller to cleanup the response.
     * and release resources.
     */
    @InternalAPI
    suspend fun executeUnsafe(): HttpResponse {
        val subject: Any = input ?: builder.body
        client.requestPipeline.execute(builder, subject)
        return client.engine.roundTrip(builder)
    }

    /**
     * Runs the response pipeline and returns the resulting transformation (without any expectations)
     */
    @InternalAPI
    suspend inline fun <reified TResponse> getPipelineResponse(httpResponse: HttpResponse): Any {
        val want = TypeInfo(TResponse::class)
        val responseContext = HttpResponseContext(httpResponse, want, executionCtx = executionCtx)
        // There are two paths for an HTTP response:
        //     1. Response payload is consumed in the pipeline (e.g. through deserialization). Resources
        //        are released immediately (and automatically) by consuming the payload.
        //
        //     2. Response payload is streaming and the end user or service call is responsible for consuming
        //        the payload and only then resources will be released.
        return client.responsePipeline.execute(responseContext, httpResponse.body)
    }

    /**
     * Run the response pipeline and transform the raw HttpResponse to the result of the pipeline execution
     * @throws ResponseTransformFailed Thrown when the pipeline result is not equal to the expected result
     */
    @InternalAPI
    suspend inline fun <reified TResponse> transformResponse(httpResponse: HttpResponse): TResponse {
        val response = getPipelineResponse<TResponse>(httpResponse)
        if (response !is TResponse) {
            // response pipeline failed to transform the raw HttResponse content into the expected output type
            throw ResponseTransformFailed(httpResponse, response::class, TResponse::class)
        }
        return response
    }

    /**
     * Execute this request and return the result of the [SdkHttpClient.responsePipeline]
     * Underlying resources are cleaned up before leaving the call.
     * @throws ResponseTransformFailed
     */
    suspend inline fun <reified TResponse> receive(): TResponse = when (TResponse::class) {
            PreparedHttpRequest::class -> this as TResponse
            HttpResponse::class -> {
                val httpResp = executeUnsafe()
                try {
                    // run the pipeline ensuring any middleware has a chance to interact with the response,
                    // there is no expectation on the result of that execution though
                    getPipelineResponse<TResponse>(httpResp)
                    httpResp as TResponse
                } finally {
                    httpResp.complete()
                }
            }
            else -> {
                val httpResp = executeUnsafe()
                try {
                    transformResponse<TResponse>(httpResp)
                } finally {
                    httpResp.complete()
                }
            }
        }

    /**
     * Execute the request and run the [block] with the result of the [SdkHttpClient.responsePipeline].
     * Resources will remain open until the block finishes, when the call returns underlying
     * resources will be cleaned up.
     * @throws ResponseTransformFailed
     */
    suspend inline fun <reified T, R> execute(crossinline block: suspend (response: T) -> R): R {
        val httpResp = executeUnsafe()
        try {
            val transformResp = transformResponse<T>(httpResp)
            return block(transformResp)
        } finally {
            // signal the response can now be discarded
            httpResp.complete()
        }
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
