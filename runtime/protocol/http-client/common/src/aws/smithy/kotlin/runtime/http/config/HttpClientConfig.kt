/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor

/**
 * The user-accessible configuration properties for the SDKs internal HTTP client facility.
 */
public interface HttpClientConfig : HttpEngineConfig {
    /**
     * Interceptors that will be executed for each SDK operation.
     * An [aws.smithy.kotlin.runtime.client.Interceptor] has access to read and modify
     * the request and response objects as they are processed by the SDK.
     * Interceptors are executed in the order they are configured and are always later than any added automatically by
     * the SDK.
     */
    public val interceptors: List<HttpInterceptor>

    /**
     * A builder for [HttpClientConfig]
     */
    public interface Builder : HttpEngineConfig.Builder {
        /**
         * Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
         * the request and response objects as they are processed by the SDK.
         * Interceptors added using this method are executed in the order they are configured and are always
         * later than any added automatically by the SDK.
         */
        public var interceptors: MutableList<HttpInterceptor>
    }
}

@DslMarker
public annotation class HttpEngineConfigDsl

/**
 * The configuration properties for setting HTTP client engine instances or configuration.
 */
public interface HttpEngineConfig {
    /**
     * Explicit HTTP engine to use when making SDK requests, when not set a default engine will be created and managed
     * on behalf of the caller.
     *
     * **NOTE**: The caller is responsible for managing the lifetime of the engine when set. The SDK
     * client will not close it when the client is closed.
     */
    public val httpClient: HttpClientEngine

    /**
     * A builder for [HttpEngineConfig]
     */
    @HttpEngineConfigDsl
    public interface Builder {
        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts,
         * concurrency, etc).
         *
         * **NOTE**: The caller is responsible for managing the lifetime of the engine when set. The SDK
         * client will not close it when the client is closed.
         */
        public var httpClient: HttpClientEngine?

        /**
         * Override configuration settings for an HTTP client engine without specifying a specific instance. The
         * resulting engine's lifecycle will be managed by the SDK (e.g., it will be closed when the client is closed).
         *
         * This method will throw an exception if the [httpClient] has been set to a specific instance.
         * @param block A builder block used to set parameters in DSL style
         */
        public fun httpClient(block: HttpClientEngineConfig.Builder.() -> Unit = {})

        /**
         * Override configuration settings for an HTTP client engine without specifying a specific instance. The
         * resulting engine's lifecycle will be managed by the SDK (e.g., it will be closed when the client is closed).
         *
         * This method will throw an exception if the [httpClient] has been set to a specific instance.
         * @param engineFactory The specific engine variant to use. Selecting a non-default variant will enable access
         * to engine-specific configuration parameters.
         * @param block A builder block used to set parameters in DSL style
         */
        public fun <B : HttpClientEngineConfig.Builder, E : HttpClientEngine> httpClient(
            engineFactory: EngineFactory<B, E>,
            block: B.() -> Unit = {},
        )

        /**
         * Build an `HttpEngineConfig` from this builder.
         */
        @InternalApi
        public fun buildHttpEngineConfig(): HttpEngineConfig
    }
}

/**
 * A factory capable of producing [HttpClientEngine] instances.
 * @param B The type of builder used to construct configuration for the engine
 * @param E The type of engine itself
 */
public interface EngineFactory<out B : HttpClientEngineConfig.Builder, E : HttpClientEngine> {
    /**
     * Gets a constructor for the engine which accepts receiver lambda which can operate on a builder
     */
    public val engineConstructor: (B.() -> Unit) -> E
}
