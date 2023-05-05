package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.config.HttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.internal.manage

private typealias ConfigApplicator = HttpClientEngineConfig.Builder.() -> Unit

@InternalApi
public class HttpEngineConfigImpl private constructor(override val httpClientEngine: HttpClientEngine) : HttpEngineConfig {
    @InternalApi
    public class BuilderImpl : HttpEngineConfig.Builder {
        private var configApplicator: ConfigApplicator = {}

        override var engineConstructor: (ConfigApplicator) -> CloseableHttpClientEngine = ::DefaultHttpEngine

        private var engineSupplier: () -> HttpClientEngine = { engineConstructor {}.manage() }

        override var httpClientEngine: HttpClientEngine? = null
            set(value) {
                field = value
                engineSupplier = when (value) {
                    null -> {
                        // Reset engine type back to default
                        engineConstructor = ::DefaultHttpEngine
                        { engineConstructor {}.manage() }
                    }
                    else -> {{ value }}
                }
                configApplicator = value?.config?.toBuilderApplicator() ?: {}
            }

        override fun httpClientEngine(block: (HttpClientEngineConfig.Builder.() -> Unit)?) {
            val previousApplicator = configApplicator
            configApplicator = { previousApplicator(); block?.invoke(this) }
            engineSupplier = { engineConstructor(configApplicator).manage() }
        }

        override fun buildHttpEngineConfig(): HttpEngineConfig = HttpEngineConfigImpl(engineSupplier())
    }
}
