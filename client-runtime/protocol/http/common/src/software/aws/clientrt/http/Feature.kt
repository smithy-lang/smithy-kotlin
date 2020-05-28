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
 */
interface Feature {
    /**
     * Install the feature to the [SdkHttpClient]. This allows the feature to wire itself up to the underlying
     * client (e.g. install interceptors for pipeline phases, etc).
     */
    fun install(client: SdkHttpClient)
}
