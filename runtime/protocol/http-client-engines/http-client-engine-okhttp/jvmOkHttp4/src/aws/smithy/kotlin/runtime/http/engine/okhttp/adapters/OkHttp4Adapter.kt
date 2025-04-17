package aws.smithy.kotlin.runtime.http.engine.okhttp.adapters

import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.http.engine.TlsContext
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.net.TlsVersion
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.fromEpochMilliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException
import kotlin.time.toJavaDuration

internal abstract class OkHttpApiVersionDetectingAdapter(
    private val requiredMajorVersion: Int,
) : DynamicClientFactoryAdapter {
    private val okHttpMajorVersion by lazy {
        runCatching {
            OkHttp
                .VERSION
                .split('.')
                .firstOrNull()
                ?.toIntOrNull()
        }
            .getOrNull()
            ?: 0
    }

    override val isValidInEnvironment: Boolean
        get() = okHttpMajorVersion == requiredMajorVersion

    protected fun buildOkHttpClient(config: OkHttpEngineConfig, metrics: HttpClientMetrics) =
        OkHttpClient.Builder().apply {
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
            val pool = buildConnectionPool(config)
            connectionPool(pool)

            val dispatcher = Dispatcher().apply {
                maxRequests = config.maxConcurrency.toInt()
                maxRequestsPerHost = config.maxConcurrencyPerHost.toInt()
            }
            dispatcher(dispatcher)

            // Log events coming from okhttp. Allocate a new listener per-call to facilitate dedicated trace spans.
            eventListenerFactory { call ->
                HttpEngineEventListener(pool, config.hostResolver, dispatcher, metrics, call)
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

    open fun buildConnectionPool(config: OkHttpEngineConfig): ConnectionPool = ConnectionPool(
        maxIdleConnections = 5, // The default from the no-arg ConnectionPool() constructor
        keepAliveDuration = config.connectionIdleTimeout.inWholeMilliseconds,
        TimeUnit.MILLISECONDS,
    )
}

internal class OkHttp4ClientFactoryAdapter : OkHttpApiVersionDetectingAdapter(4) {
    override fun buildClient(config: OkHttpEngineConfig, metrics: HttpClientMetrics): ClientAdapter =
        OkHttp4ClientAdapter(buildOkHttpClient(config, metrics), metrics)
}

internal open class OkHttp4ClientAdapter(
    private val client: OkHttpClient,
    private val metrics: HttpClientMetrics,
) : ClientAdapter {
    override fun close() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    override suspend fun roundTrip(
        context: ExecutionContext,
        callContext: CoroutineContext,
        request: HttpRequest,
    ): HttpCall {
        val engineRequest = request.toOkHttpRequest(context, callContext, metrics)
        val engineCall = client.newCall(engineRequest)

        val engineResponse = mapOkHttpExceptions {
            @OptIn(ExperimentalCoroutinesApi::class)
            engineCall.executeAsync()
        }

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

// Copied from okhttp3 5.x:
// https://github.com/square/okhttp/blob/d58da0a65b7f9cdbdf25b198e804153164ae729f/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt
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
                    continuation.resume(response) { cause, _, _ ->
                        response.closeQuietly()
                    }
                }
            },
        )
    }

private fun minTlsConnectionSpec(tlsContext: TlsContext): ConnectionSpec {
    val minVersion = tlsContext.minVersion ?: TlsVersion.TLS_1_2
    val okHttpTlsVersions = TlsVersion
        .entries
        .filter { it >= minVersion }
        .sortedDescending() // Prioritize higher TLS versions first
        .map(::toOkHttpTlsVersion)
        .toTypedArray()
    return ConnectionSpec
        .Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(*okHttpTlsVersions)
        .build()
}

private fun toOkHttpTlsVersion(sdkTlsVersion: TlsVersion): okhttp3.TlsVersion = when (sdkTlsVersion) {
    TlsVersion.TLS_1_0 -> okhttp3.TlsVersion.TLS_1_0
    TlsVersion.TLS_1_1 -> okhttp3.TlsVersion.TLS_1_1
    TlsVersion.TLS_1_2 -> okhttp3.TlsVersion.TLS_1_2
    TlsVersion.TLS_1_3 -> okhttp3.TlsVersion.TLS_1_3
}
