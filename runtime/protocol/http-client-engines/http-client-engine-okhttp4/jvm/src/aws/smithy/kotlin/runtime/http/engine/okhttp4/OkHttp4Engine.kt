/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp4

import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.internal.closeQuietly
import okio.IOException
import kotlin.coroutines.resumeWithException

internal const val TELEMETRY_SCOPE = "aws.smithy.kotlin.runtime.http.engine.okhttp4"

/**
 * [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] based on OkHttp3-4.x.
 */
public class OkHttp4Engine(
    override val config: OkHttpEngineConfig,
) : HttpClientEngineBase("OkHttp4") {
    public constructor() : this(OkHttpEngineConfig.Default)

    public companion object : EngineFactory<OkHttpEngineConfig.Builder, OkHttp4Engine> {
        /**
         * Initializes a new [OkHttp4Engine] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: OkHttpEngineConfig.Builder.() -> Unit): OkHttp4Engine =
            OkHttp4Engine(OkHttpEngineConfig(block))

        override val engineConstructor: (OkHttpEngineConfig.Builder.() -> Unit) -> OkHttp4Engine = ::invoke
    }

    private val metrics = HttpClientMetrics(TELEMETRY_SCOPE, config.telemetryProvider)
    private val client = config.buildClient(metrics)

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        val callContext = callContext()

        val engineRequest = request.toOkHttpRequest(context, callContext, metrics)
        val engineCall = client.newCall(engineRequest)

        @OptIn(ExperimentalCoroutinesApi::class)
        val engineResponse = mapOkHttpExceptions { engineCall.executeAsync() }

        val response = engineResponse.toSdkResponse()
        val requestTime = Instant.fromEpochMilliseconds(engineResponse.sentRequestAtMillis)
        val responseTime = Instant.fromEpochMilliseconds(engineResponse.receivedResponseAtMillis)

        return OkHttpCall(request, response, requestTime, responseTime, callContext, engineCall).also { call ->
            callContext.job.invokeOnCompletion { cause ->
                // If cause is non-null that means the job was cancelled (CancellationException) or failed (anything
                // else). In both cases we need to ensure that the engine-side resources are cleaned up completely
                // since they wouldn't otherwise be. https://github.com/smithy-lang/smithy-kotlin/issues/1061
                if (cause != null) call.cancelInFlight()
                engineResponse.body?.close()
            }
        }
    }
}

// Copied from okhttp3 5.0.0-alpha.14
@ExperimentalCoroutinesApi // resume with a resource cleanup.
private suspend fun Call.executeAsync(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            this.cancel()
        }
        this.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response) {
                        response.closeQuietly()
                    }
                }
            },
        )
    }
