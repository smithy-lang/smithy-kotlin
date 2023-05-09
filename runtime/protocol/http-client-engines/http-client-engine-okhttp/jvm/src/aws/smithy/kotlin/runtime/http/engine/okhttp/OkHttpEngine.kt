/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.config.TlsVersion
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.TlsContext
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlinx.coroutines.job
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration
import aws.smithy.kotlin.runtime.config.TlsVersion as SdkTlsVersion
import okhttp3.TlsVersion as OkHttpTlsVersion

/**
 * [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] based on OkHttp.
 */
public class OkHttpEngine(
    override val config: OkHttpEngineConfig,
) : HttpClientEngineBase("OkHttp") {
    public constructor() : this(OkHttpEngineConfig.Default)

    public companion object {
        /**
         * Initializes a new [OkHttpEngine] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: OkHttpEngineConfig.Builder.() -> Unit): OkHttpEngine =
            OkHttpEngine(OkHttpEngineConfig(block))
    }

    private val client = config.buildClient()

    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        val callContext = callContext()

        val engineRequest = request.toOkHttpRequest(context, callContext)
        val engineCall = client.newCall(engineRequest)
        val engineResponse = mapOkHttpExceptions { engineCall.executeAsync() }

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

        connectionSpecs(listOf(minTlsConnectionSpec(config.tlsContext), ConnectionSpec.CLEARTEXT))

        // Transient connection errors are handled by retry strategy (exceptions are wrapped and marked retryable
        // appropriately internally). We don't want inner retry logic that inflates the number of retries.
        retryOnConnectionFailure(false)

        connectTimeout(config.connectTimeout.toJavaDuration())
        readTimeout(config.socketReadTimeout.toJavaDuration())
        writeTimeout(config.socketWriteTimeout.toJavaDuration())

        // use our own pool configured with the timeout settings taken from config
        val pool = ConnectionPool(
            maxIdleConnections = 5, // The default from the no-arg ConnectionPool() constructor
            keepAliveDuration = config.connectionIdleTimeout.inWholeMilliseconds,
            TimeUnit.MILLISECONDS,
        )
        connectionPool(pool)

        // Configure a dispatcher that uses maxConnections as a proxy for maxRequests. Note that this isn't precisely
        // the same since some protocols (e.g., HTTP2) may use a single connection for multiple requests.
        val dispatcher = Dispatcher().apply {
            maxRequests = config.maxConnections.toInt()
            maxRequestsPerHost = config.maxConnectionsPerHost.toInt()
        }
        dispatcher(dispatcher)

        // Log events coming from okhttp. Allocate a new listener per-call to facilitate dedicated trace spans.
        eventListenerFactory { call -> HttpEngineEventListener(pool, config.hostResolver, call) }

        // map protocols
        if (config.tlsContext.alpn.isNotEmpty()) {
            val protocols = config.tlsContext.alpn.mapNotNull {
                when (it) {
                    AlpnId.HTTP1_1 -> Protocol.HTTP_1_1
                    AlpnId.HTTP2 -> Protocol.HTTP_2
                    AlpnId.H2_PRIOR_KNOWLEDGE -> Protocol.H2_PRIOR_KNOWLEDGE
                    else -> null
                }
            }
            protocols(protocols)
        }

        proxySelector(OkHttpProxySelector(config.proxySelector))
        proxyAuthenticator(OkHttpProxyAuthenticator(config.proxySelector))

        dns(OkHttpDns(config.hostResolver))
    }.build()
}

private fun minTlsConnectionSpec(tlsContext: TlsContext): ConnectionSpec {
    val minVersion = tlsContext.minVersion ?: TlsVersion.TLS_1_2
    val okHttpTlsVersions = SdkTlsVersion
        .values()
        .filter { it >= minVersion }
        .sortedDescending() // Prioritize higher TLS versions first
        .map(::toOkHttpTlsVersion)
        .toTypedArray()
    return ConnectionSpec
        .Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(*okHttpTlsVersions)
        .build()
}

private fun toOkHttpTlsVersion(sdkTlsVersion: SdkTlsVersion): OkHttpTlsVersion = when (sdkTlsVersion) {
    SdkTlsVersion.TLS_1_0 -> OkHttpTlsVersion.TLS_1_0
    SdkTlsVersion.TLS_1_1 -> OkHttpTlsVersion.TLS_1_1
    SdkTlsVersion.TLS_1_2 -> OkHttpTlsVersion.TLS_1_2
    SdkTlsVersion.TLS_1_3 -> OkHttpTlsVersion.TLS_1_3
}
