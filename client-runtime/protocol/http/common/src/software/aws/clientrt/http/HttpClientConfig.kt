/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
