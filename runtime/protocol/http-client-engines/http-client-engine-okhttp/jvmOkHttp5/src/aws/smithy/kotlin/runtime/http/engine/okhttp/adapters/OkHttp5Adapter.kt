package aws.smithy.kotlin.runtime.http.engine.okhttp.adapters

import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.ConnectionIdleMonitor
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngineConfig
import okhttp3.ConnectionPool
import okhttp3.ExperimentalOkHttpApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class OkHttp5ClientFactoryAdapter : OkHttpApiVersionDetectingAdapter(5) {
    override fun buildClient(config: OkHttpEngineConfig, metrics: HttpClientMetrics): ClientAdapter =
        OkHttp5ClientAdapter(buildOkHttpClient(config, metrics), metrics)

    override fun buildConnectionPool(config: OkHttpEngineConfig): ConnectionPool =
        config.connectionIdlePollingInterval?.let { connectionIdlePollingInterval ->
            @OptIn(ExperimentalOkHttpApi::class)
            ConnectionPool(
                maxIdleConnections = 5, // The default from the no-arg ConnectionPool() constructor
                keepAliveDuration = config.connectionIdleTimeout.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
                ConnectionIdleMonitor(connectionIdlePollingInterval),
            )
        } ?: super.buildConnectionPool(config)
}

private class OkHttp5ClientAdapter(
    client: OkHttpClient,
    metrics: HttpClientMetrics,
) : OkHttp4ClientAdapter(client, metrics)
