/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

/**
 * Used to identify a particular feature
 */
class FeatureKey<T>(val name: String)

/**
 * Factory responsible for creating an instance of a feature and configuring it
 */
interface HttpClientFeatureFactory<TConfig, TFeature : Feature> {
    /**
     * Key used to identify this feature
     */
    val key: FeatureKey<TFeature>

    /**
     * Create a new feature instance, optionally configure it and return it
     */
    fun create(block: TConfig.() -> Unit = {}): TFeature
}

/**
 * An SdkHttpClient feature is an interceptor/middleware component that can self configure itself
 * on an HttpClient. This allows the component to tap into whichever part of the HTTP client it needs to
 * (usually the request or response transform pipelines).
 *
 * Features are registered at the [software.aws.clientrt.http.SdkHttpClient] level and are executed
 * on every request/response. The data flowing through those pipelines changes with every call though.
 *
 * [ExecutionContext] is the request and response pipeline per/operation context (metadata) that features can use
 * to drive behavior that is specific to a particular request or response.
 */
interface Feature {
    /**
     * Install the feature to the [SdkHttpClient]. This allows the feature to wire itself up to the underlying
     * client (e.g. install interceptors for pipeline phases, etc).
     */
    fun install(client: SdkHttpClient)
}
