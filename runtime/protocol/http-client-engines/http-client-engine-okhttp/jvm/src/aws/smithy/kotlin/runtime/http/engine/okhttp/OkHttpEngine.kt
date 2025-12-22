/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.config.EngineFactory
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.TlsContext
import aws.smithy.kotlin.runtime.http.engine.callContext
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.closeIfCloseable
import aws.smithy.kotlin.runtime.net.TlsVersion
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import okhttp3.*
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.time.toJavaDuration
import aws.smithy.kotlin.runtime.net.TlsVersion as SdkTlsVersion
import okhttp3.TlsVersion as OkHttpTlsVersion

/**
 * [aws.smithy.kotlin.runtime.http.engine.HttpClientEngine] based on OkHttp.
 */
public class OkHttpEngine private constructor(
    override val config: OkHttpEngineConfig,
    private val userProvidedClient: OkHttpClient?,
) : HttpClientEngineBase("OkHttp") {
    public constructor() : this(OkHttpEngineConfig.Default, null)

    public constructor(config: OkHttpEngineConfig) : this(config, null)

    public constructor(client: OkHttpClient) : this(OkHttpEngineConfig.Default, client)

    public companion object : EngineFactory<OkHttpEngineConfig.Builder, OkHttpEngine> {
        /**
         * Initializes a new [OkHttpEngine] via a DSL builder block
         * @param block A receiver lambda which sets the properties of the config to be built
         */
        public operator fun invoke(block: OkHttpEngineConfig.Builder.() -> Unit): OkHttpEngine =
            OkHttpEngine(OkHttpEngineConfig(block), null)

        override val engineConstructor: (OkHttpEngineConfig.Builder.() -> Unit) -> OkHttpEngine = ::invoke
    }

    // Create a single shared connection monitoring listener if idle polling is enabled
    private val connectionMonitoringListener: EventListener? =
        config.connectionIdlePollingInterval?.let {
            ConnectionMonitoringEventListener(it)
        }

    private val metrics = HttpClientMetrics(TELEMETRY_SCOPE, config.telemetryProvider)
    private val client: OkHttpClient by lazy {
        userProvidedClient?.withMetrics(metrics, config)
            ?: config.buildClient(metrics, connectionMonitoringListener)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
        val callContext = callContext()

        val engineRequest = request.toOkHttpRequest(context, callContext, metrics)
        val engineCall = client.newCall(engineRequest)

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
                engineResponse.body.close()
            }
        }
    }

    override fun shutdown() {
        connectionMonitoringListener?.closeIfCloseable()
        metrics.close()
        if (userProvidedClient == null) {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}

/**
 * Convert SDK version of HTTP configuration to OkHttp specific configuration and return the configured client
 */
@InternalApi
public fun OkHttpEngineConfig.buildClient(
    metrics: HttpClientMetrics,
    vararg clientScopedEventListeners: EventListener?,
): OkHttpClient {
    val config = this

    return OkHttpClient.Builder().apply {
        // don't follow redirects
        followRedirects(false)
        followSslRedirects(false)

        connectionSpecs(listOf(tlsConnectionSpec(config.tlsContext, config.cipherSuites), ConnectionSpec.CLEARTEXT))

        config.trustManager?.let {
            val sslContext = createSslContext(it, config.keyManager)
            sslSocketFactory(sslContext.socketFactory, trustManager!!)
        }

        config.certificatePinner?.let(::certificatePinner)
        config.hostnameVerifier?.let(::hostnameVerifier)

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

        val dispatcher = Dispatcher().apply {
            maxRequests = config.maxConcurrency.toInt()
            maxRequestsPerHost = config.maxConcurrencyPerHost.toInt()
        }
        dispatcher(dispatcher)

        // Log events coming from okhttp. Allocate a new listener per-call to facilitate dedicated trace spans.
        eventListenerFactory { call ->
            EventListenerChain(
                listOfNotNull(
                    HttpEngineEventListener(pool, config.hostResolver, dispatcher, metrics, call),
                    *clientScopedEventListeners,
                ),
            )
        }

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

        addInterceptor(MetricsInterceptor)
    }.build()
}

// Configure a user-provided client to collect SDK metrics
private fun OkHttpClient.withMetrics(metrics: HttpClientMetrics, config: OkHttpEngineConfig) = newBuilder().apply {
    eventListenerFactory { call ->
        EventListenerChain(
            listOf(
                HttpEngineEventListener(connectionPool, config.hostResolver, dispatcher, metrics, call),
            ),
        )
    }
    addInterceptor(MetricsInterceptor)
}.build()

private fun tlsConnectionSpec(tlsContext: TlsContext, cipherSuites: List<String>?): ConnectionSpec {
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
        .apply {
            cipherSuites?.toTypedArray()?.let(::cipherSuites)
        }
        .build()
}

private fun toOkHttpTlsVersion(sdkTlsVersion: SdkTlsVersion): OkHttpTlsVersion = when (sdkTlsVersion) {
    SdkTlsVersion.TLS_1_0 -> OkHttpTlsVersion.TLS_1_0
    SdkTlsVersion.TLS_1_1 -> OkHttpTlsVersion.TLS_1_1
    SdkTlsVersion.TLS_1_2 -> OkHttpTlsVersion.TLS_1_2
    SdkTlsVersion.TLS_1_3 -> OkHttpTlsVersion.TLS_1_3
}

/**
 * Creates an SSL context with custom trust and key managers
 */
private fun createSslContext(trustManager: X509TrustManager, keyManager: KeyManager?): SSLContext {
    val keyManagers = keyManager?.let { arrayOf(it) }
    val trustManagers = arrayOf(trustManager)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers, trustManagers, null)

    return sslContext
}
