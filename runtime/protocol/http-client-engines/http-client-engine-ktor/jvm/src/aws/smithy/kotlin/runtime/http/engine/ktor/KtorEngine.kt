/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.logging.*
import aws.smithy.kotlin.runtime.time.Instant
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.statement.HttpStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import aws.smithy.kotlin.runtime.http.response.HttpResponse as SdkHttpResponse

/**
 * JVM [HttpClientEngine] backed by Ktor
 */
class KtorEngine(val config: HttpClientEngineConfig) : HttpClientEngineBase("ktor") {
    val client: HttpClient = HttpClient(OkHttp) {
        // TODO - propagate applicable client engine config to OkHttp engine

        // do not throw exceptions if status code < 300, error handling is expected by generated clients
        expectSuccess = false
    }
    private val logger = Logger.getLogger<KtorEngine>()

    override suspend fun roundTrip(request: HttpRequest): HttpCall {
        val callContext = callContext()

        val respChannel = Channel<HttpCall>(Channel.RENDEZVOUS)

        // run the request in another coroutine to allow streaming body to be handled
        launch(callContext + Dispatchers.IO) {
            try {
                execute(callContext, request, respChannel)
            } catch (ex: Exception) {
                // signal the HTTP response isn't coming
                respChannel.close(ex)
            }
        }

        // wait for the response to be available, the content will be read as a stream
        logger.trace("waiting on response to be available")

        try {
            val resp = respChannel.receive()
            logger.trace("response is available continuing")
            return resp
        } catch (ex: Exception) {
            throw logger.throwing(ex)
        }
    }

    private suspend fun execute(
        callContext: CoroutineContext,
        sdkRequest: HttpRequest,
        channel: SendChannel<HttpCall>
    ) {
        val builder = KtorRequestAdapter(sdkRequest, callContext).toBuilder()
        val waiter = Waiter()
        val reqTime = Instant.now()
        client.request<HttpStatement>(builder).execute { httpResp ->
            val respTime = Instant.now()
            // we have a lifetime problem here...the stream (and HttpResponse instance) are only valid
            // until the end of this block. We don't know if the consumer wants to read the content fully or
            // stream it. We need to wait until the entire content has been read before leaving the block and
            // releasing the underlying network resources. We do this by blocking until the request job
            // completes, at which point we signal it's safe to exit the block and release the underlying resources.
            callContext.job.invokeOnCompletion { waiter.signal() }

            val body = KtorHttpBody(httpResp.content)

            // copy the headers so that we no longer depend on the underlying ktor HttpResponse object
            // outside of the body content (which will signal once read that it is safe to exit the block)
            val headers = Headers { appendAll(KtorHeaders(httpResp.headers)) }

            val resp = SdkHttpResponse(
                HttpStatusCode.fromValue(httpResp.status.value),
                headers,
                body,
            )

            logger.trace("signalling response")
            val call = HttpCall(sdkRequest, resp, reqTime, respTime, callContext)
            channel.send(call)

            logger.trace("waiting on body to be consumed")
            // wait for the receiving end to finish with the HTTP body
            waiter.wait()
            logger.trace("request done")
        }
    }

    override fun close() {
        client.close()
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
