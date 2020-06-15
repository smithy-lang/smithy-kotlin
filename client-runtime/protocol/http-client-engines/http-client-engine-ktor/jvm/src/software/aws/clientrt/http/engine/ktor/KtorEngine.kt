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
package software.aws.clientrt.http.engine.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.statement.HttpStatement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse as SdkHttpResponse
import software.aws.clientrt.http.response.HttpResponsePipeline

/**
 * JVM [HttpClientEngine] backed by Ktor
 */
class KtorEngine(val config: HttpClientEngineConfig) : HttpClientEngine {
    val client: HttpClient

    init {
        client = HttpClient(OkHttp) {
            // TODO - propagate applicable client engine config to OkHttp engine
        }
    }

    override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): SdkHttpResponse {
        val callContext = coroutineContext

        val respChannel = Channel<SdkHttpResponse>(Channel.RENDEZVOUS)

        // run the request in another coroutine to allow streaming body to be handled
        GlobalScope.launch(callContext + Dispatchers.IO) {
            try {
                execute(callContext, requestBuilder, respChannel)
            } catch (ex: Exception) {
                // signal the HTTP response isn't coming
                respChannel.close(ex)
            }
        }

        // TODO - convert to trace/debug logging when available
        // wait for the response to be available, the content will be read as a stream
        println("(${Thread.currentThread().name}) ktor engine: waiting on response to be available")

        try {
            val resp = respChannel.receive()
            println("(${Thread.currentThread().name}) ktor engine: response is available continuing")
            return resp
        } catch (ex: Exception) {
            println(ex)
            throw ex
        }
    }

    private suspend fun execute(
        callContext: CoroutineContext,
        sdkBuilder: HttpRequestBuilder,
        channel: SendChannel<SdkHttpResponse>
    ) {
        val builder = KtorRequestAdapter(sdkBuilder, callContext).toBuilder()
        val waiter = Waiter()
        client.request<HttpStatement>(builder).execute { httpResp ->
            // we have a lifetime problem here...the stream (and HttpResponse instance) are only valid
            // until the end of this block. We don't know if the consumer wants to read the content fully or
            // stream it. We need to wait until the entire content has been read before leaving the block and
            // releasing the underlying network resources...

            // when the body has been read fully we will signal which allows the current block to exit
            val body = KtorHttpBody(httpResp.content) { waiter.signal() }

            // copy the headers so that we no longer depend on the underlying ktor HttpResponse object
            // outside of the body content (which will signal once read that it is safe to exit the block)
            val headers = Headers { appendAll(KtorHeaders(httpResp.headers)) }

            val resp = SdkHttpResponse(
                HttpStatusCode.fromValue(httpResp.status.value),
                headers,
                body,
                sdkBuilder.build()
            )

            println("(${Thread.currentThread().name}) ktor engine: signalling response")
            channel.send(resp)

            println("(${Thread.currentThread().name}) ktor engine: waiting on body to be consumed")
            // wait for the receiving end to finish with the HTTP body
            waiter.wait()
            println("(${Thread.currentThread().name}) ktor engine: request done")
        }
    }

    override fun close() {
        client.close()
    }

    override fun install(client: SdkHttpClient) {
        super.install(client)
        client.responsePipeline.intercept(HttpResponsePipeline.Finalize) {
            // ensure the response body is consumed and resources are released
            val body = context.response.body
            when (body) {
                is HttpBody.Streaming -> {
                    val source = body.readFrom()
                    if (source.isClosedForRead) {
                        // If the response is a streaming body the end user is responsible for ensuring it gets read and closed.
                        // This either happens by reading the content or explicit cancellation. If the source
                        // is closed we just ensure that it is cancelled (which could happen if there was no content to read).
                        // This releases the ktor client coroutine if it was waiting for a body to be consumed
                        source.cancel(null)
                    }
                }
            }
        }
    }
}

/**
 * Simple notify mechanism that waits for a signal
 */
internal class Waiter {
    private val channel = Channel<Unit>(0)

    // wait for the signal
    suspend fun wait() { channel.receive() }

    // give the signal to continue
    fun signal() { channel.offer(Unit) }
}
