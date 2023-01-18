/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.config

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor

/**
 * The user-accessible configuration properties for the SDKs internal HTTP client facility.
 */
public interface HttpClientConfig {
    /**
     * Explicit HTTP engine to use when making SDK requests, when not set a default engine will be created and managed
     * on behalf of the caller.
     *
     * **NOTE**: The caller is responsible for managing the lifetime of the engine when set. The SDK
     * client will not close it when the client is closed.
     */
    public val httpClientEngine: HttpClientEngine?

    /**
     * Interceptors that will be executed for each SDK operation.
     * An [aws.smithy.kotlin.runtime.client.Interceptor] has access to read and modify
     * the request and response objects as they are processed by the SDK.
     * Interceptors are executed in the order they are configured and are always later than any added automatically by
     * the SDK.
     */
    public val interceptors: List<HttpInterceptor>

    public interface Builder {
        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts,
         * concurrency, etc).
         *
         * **NOTE**: The caller is responsible for managing the lifetime of the engine when set. The SDK
         * client will not close it when the client is closed.
         */
        public var httpClientEngine: HttpClientEngine?

        /**
         * Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
         * the request and response objects as they are processed by the SDK.
         * Interceptors added using this method are executed in the order they are configured and are always
         * later than any added automatically by the SDK.
         */
        public var interceptors: MutableList<HttpInterceptor>
    }
}
