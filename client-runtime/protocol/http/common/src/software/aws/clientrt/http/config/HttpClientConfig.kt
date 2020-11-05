package software.aws.clientrt.http.config

import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.engine.HttpClientEngineConfig

/**
 * The user-accessible configuration properties for the SDKs internal HTTP client facility.
 */
interface HttpClientConfig {
    /**
     * Allows for overriding the default HTTP client engine.
     */
    val httpClientEngine: HttpClientEngine?

    /**
     * Allows for supplying a custom configuration for either the default engine or a user-supplied engine.
     */
    val httpClientEngineConfig: HttpClientEngineConfig?
}
