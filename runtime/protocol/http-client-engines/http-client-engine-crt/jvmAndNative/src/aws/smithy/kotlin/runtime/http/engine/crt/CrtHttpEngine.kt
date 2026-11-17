/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.http.HttpVersion
import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal const val DEFAULT_WINDOW_SIZE_BYTES: Int = 16 * 1024
internal const val CHUNK_BUFFER_SIZE: Long = 64 * 1024

/**
 * [HttpClientEngine] based on the AWS Common Runtime HTTP client
 */
public class CrtHttpEngine(public override val config: CrtHttpEngineConfig) : HttpClientEngineBase("crt") {
    public constructor() : this(CrtHttpEngineConfig.Default)

    public companion object : EngineFactory<CrtHttpEngineConfig.Builder, CrtHttpEngine> {
        public operator fun invoke(block: CrtHttpEngineConfig.Builder.() -> Unit): CrtHttpEngine = CrtHttpEngine(CrtHttpEngineConfig(block))

        override val engineConstructor: (CrtHttpEngineConfig.Builder.() -> Unit) -> CrtHttpEngine = ::invoke
    }

    // FIXME - re-enable when SLF4j default is available
    // init {
    //     if (config.socketWriteTimeout != CrtHttpEngineConfig.Default.socketWriteTimeout) {
    //         logger.warn { "CrtHttpEngine does not support socketWriteTimeout(${config.socketWriteTimeout}); ignoring" }
    //     }
    //
    //     if (config.hostResolver !== HostResolver.Default) {
    //         // FIXME - there is no way to currently plugin a JVM based host resolver to CRT. (see V804672153)
    //         logger.warn { "CrtHttpEngine does not support custom HostResolver implementations; ignoring" }
    //     }
    // }

    private val requestLimiter = Semaphore(config.maxConcurrency.toInt())
    private val connectionManager = ConnectionManager(config)

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall = requestLimiter.withPermit {
        val callContext = callContext()
        val logger = callContext.logger<CrtHttpEngine>()

        // LIFETIME: connection will be released back to the pool/manager when
        // the response completes OR on exception (both handled by the completion handler registered on the stream
        // handler)
        val conn = connectionManager.acquire(request)
        logger.trace { "Acquired connection ${conn.id} (${conn.version})" }

        val respHandler = SdkStreamResponseHandler(
            conn,
            callContext,
            config.initialWindowSizeBytes,
            config.socketReadTimeout,
        )
        callContext.job.invokeOnCompletion {
            logger.trace { "completing handler; cause=$it" }
            // ensures the stream is driven to completion regardless of what the downstream consumer does
            respHandler.complete()
        }

        val reqTime = Instant.now()

        // completes once CRT has taken the whole request body; the read timeout only applies after that
        val requestSent = Job()

        val stream = mapCrtException {
            val engineRequest = if (conn.version == HttpVersion.HTTP_2) {
                request.toHttp2Request(callContext)
            } else {
                request.toCrtRequest(callContext)
            }.let { crtRequest ->
                val body = crtRequest.body
                if (body == null || request.body.contentLength == 0L) {
                    // a chunked body has no body stream; it's fully sent below before waiting for the response
                    requestSent.complete()
                    crtRequest
                } else {
                    crtRequest.copy(body = SendCompletionBodyStream(body, requestSent))
                }
            }
            conn.makeRequest(engineRequest, respHandler).also { stream ->
                stream.activate()
            }
        }

        callContext.job.invokeOnCompletion {
            stream.close()
        }

        if (conn.version != HttpVersion.HTTP_2 && request.isChunked) {
            withContext(SdkDispatchers.IO) {
                stream.sendChunkedBody(request.body)
            }
        }

        val resp = respHandler.waitForResponse(requestSent)

        return HttpCall(request, resp, reqTime, Instant.now(), callContext)
    }

    override fun shutdown() {
        // close all resources
        // SAFETY: shutdown is only invoked once AND only after all requests have completed and no more are coming
        connectionManager.close()
    }
}

/**
 * Completes [sent] once [delegate] reports that the whole request body has been written.
 */
private class SendCompletionBodyStream(
    private val delegate: HttpRequestBodyStream,
    private val sent: CompletableJob,
) : HttpRequestBodyStream {
    override fun sendRequestBody(buffer: MutableBuffer): Boolean = delegate.sendRequestBody(buffer).also { done ->
        if (done) sent.complete()
    }

    override fun resetPosition(): Boolean = delegate.resetPosition()
}
