/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

/**
 * Dsl marker for [SdkHttpClient] dsl.
 */
@DslMarker
annotation class HttpClientDsl

/**
 * Configuration settings for [SdkHttpClient]
 */
@HttpClientDsl
class HttpClientConfig {
    private val features: MutableMap<FeatureKey<*>, Feature> = mutableMapOf()

    /**
     * Install a specific [feature] and optionally [configure] it.
     */
    fun <TConfig : Any, TFeature : Feature> install(
        feature: HttpClientFeatureFactory<TConfig, TFeature>,
        configure: TConfig.() -> Unit = {}
    ) {
        require(!features.contains(feature.key)) { "feature $feature has already been installed and configured" }
        val instance = feature.create(configure)
        features[feature.key] = instance
    }

    /**
     * Allow configured features to install themselves into the HTTP Client
     */
    fun install(client: SdkHttpClient) {
        features.values.forEach { it.install(client) }
    }
}
