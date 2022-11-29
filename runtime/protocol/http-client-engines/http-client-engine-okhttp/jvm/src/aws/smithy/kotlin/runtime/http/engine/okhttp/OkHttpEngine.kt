/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlinx.coroutines.job
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

/**
 * [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] based on OkHttp.
 */
public class OkHttpEngine(
    private val config: OkHttpEngineConfig,
) : HttpClientEngineBase("OkHttp") {
    public constructor() : this(OkHttpEngineConfig.Default)

    public companion object {
        public operator fun invoke(block: OkHttpEngineConfig.Builder.() -> Unit): OkHttpEngine = OkHttpEngine(
            OkHttpEngineConfig.Builder().apply(block).build(),
        )
    }

    private val client = config.buildClient()

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        val callContext = callContext()

        val engineRequest = request.toOkHttpRequest(context, callContext)
        val engineCall = client.newCall(engineRequest)
        val engineResponse = engineCall.executeAsync()

        callContext.job.invokeOnCompletion {
            engineResponse.body.close()
        }

        val response = engineResponse.toSdkResponse()
        val requestTime = Instant.fromEpochMilliseconds(engineResponse.sentRequestAtMillis)
        val responseTime = Instant.fromEpochMilliseconds(engineResponse.receivedResponseAtMillis)

        return HttpCall(request, response, requestTime, responseTime, callContext)
    }

    override fun shutdown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/**
 * Convert SDK version of HTTP configuration to OkHttp specific configuration and return the configured client
 */
private fun OkHttpEngineConfig.buildClient(): OkHttpClient {
    val config = this

    return OkHttpClient.Builder().apply {
        // don't follow redirects
        followRedirects(false)
        followSslRedirects(false)

        // see: https://github.com/ktorio/ktor/issues/1708#issuecomment-609988128
        retryOnConnectionFailure(true)

        connectTimeout(config.connectTimeout.toJavaDuration())
        readTimeout(config.socketReadTimeout.toJavaDuration())
        writeTimeout(config.socketWriteTimeout.toJavaDuration())

        // use our own pool configured with the settings taken from config
        val pool = ConnectionPool(
            maxIdleConnections = config.maxConnections.toInt(),
            keepAliveDuration = config.connectionIdleTimeout.inWholeMilliseconds,
            TimeUnit.MILLISECONDS,
        )
        connectionPool(pool)

        // Log events coming from okhttp. Allocate a new listener per-call to facilitate dedicated trace spans.
        eventListenerFactory { call -> HttpEngineEventListener(pool, call) }

        // map protocols
        if (config.alpn.isNotEmpty()) {
            val protocols = config.alpn.mapNotNull {
                when (it) {
                    AlpnId.HTTP1_1 -> Protocol.HTTP_1_1
                    AlpnId.HTTP2 -> Protocol.HTTP_2
                    else -> null
                }
            }
            protocols(protocols)
        }

        proxySelector(OkHttpProxySelector(config.proxySelector))
        proxyAuthenticator(OkHttpProxyAuthenticator(config.proxySelector))
    }.build()
}
