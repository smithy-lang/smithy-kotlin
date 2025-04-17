package aws.smithy.kotlin.runtime.http.engine.okhttp.adapters

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngineConfig

internal object DefaultClientFactoryAdapter : ClientFactoryAdapter {
    private val candidates = setOf(
        OkHttp5ClientFactoryAdapter(),
        OkHttp4ClientFactoryAdapter(),
    )

    private val delegate by lazy {
        candidates.firstOrNull { it.isValidInEnvironment } ?: throw ClientException("No supported OkHttp client found")
    }

    override fun buildClient(config: OkHttpEngineConfig, metrics: HttpClientMetrics) =
        delegate.buildClient(config, metrics)
}
