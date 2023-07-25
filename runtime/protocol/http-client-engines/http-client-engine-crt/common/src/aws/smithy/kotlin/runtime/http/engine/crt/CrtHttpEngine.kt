/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val DEFAULT_WINDOW_SIZE_BYTES: Int = 16 * 1024
internal const val CHUNK_BUFFER_SIZE: Long = 64 * 1024

/**
 * [HttpClientEngine] based on the AWS Common Runtime HTTP client
 */
public class CrtHttpEngine(public override val config: CrtHttpEngineConfig) : HttpClientEngineBase("crt") {
    public constructor() : this(CrtHttpEngineConfig.Default)

    public companion object : EngineFactory<CrtHttpEngineConfig.Builder, CrtHttpEngine> {
        public operator fun invoke(block: CrtHttpEngineConfig.Builder.() -> Unit): CrtHttpEngine =
            CrtHttpEngine(CrtHttpEngineConfig(block))

        override val engineConstructor: (CrtHttpEngineConfig.Builder.() -> Unit) -> CrtHttpEngine = ::invoke
    }

    // FIXME - re-enable when SLF4j default is available
    // init {
    //     if (config.socketReadTimeout != CrtHttpEngineConfig.Default.socketReadTimeout) {
    //         logger.warn { "CrtHttpEngine does not support socketReadTimeout(${config.socketReadTimeout}); ignoring" }
    //     }
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
        logger.trace { "Acquired connection ${conn.id}" }

        val respHandler = SdkStreamResponseHandler(conn, callContext)
        callContext.job.invokeOnCompletion {
            logger.trace { "completing handler; cause=$it" }
            // ensures the stream is driven to completion regardless of what the downstream consumer does
            respHandler.complete()
        }

        val reqTime = Instant.now()
        val engineRequest = request.toCrtRequest(callContext)

        val stream = mapCrtException {
            conn.makeRequest(engineRequest, respHandler).also { stream ->
                stream.activate()
            }
        }

        if (request.isChunked) {
            withContext(SdkDispatchers.IO) {
                stream.sendChunkedBody(request.body)
            }
        }

        val resp = respHandler.waitForResponse()

        return HttpCall(request, resp, reqTime, Instant.now(), callContext)
    }

    override fun shutdown() {
        // close all resources
        // SAFETY: shutdown is only invoked once AND only after all requests have completed and no more are coming
        connectionManager.close()
    }
}
