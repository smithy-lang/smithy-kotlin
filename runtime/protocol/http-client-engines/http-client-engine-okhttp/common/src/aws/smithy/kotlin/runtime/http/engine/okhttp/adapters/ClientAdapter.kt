package aws.smithy.kotlin.runtime.http.engine.okhttp.adapters

import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlin.coroutines.CoroutineContext

internal interface ClientFactoryAdapter {
    fun buildClient(
        config: OkHttpEngineConfig,
        metrics: HttpClientMetrics,
    ): ClientAdapter
}

internal interface DynamicClientFactoryAdapter : ClientFactoryAdapter {
    val isValidInEnvironment: Boolean
}

internal interface ClientAdapter : Closeable {
    suspend fun roundTrip(context: ExecutionContext, callContext: CoroutineContext, request: HttpRequest): HttpCall
}
