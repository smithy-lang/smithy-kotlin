/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import software.aws.clientrt.http.operation.SdkHttpOperation

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
 * A feature is an interceptor/middleware component that can self configure itself
 * for an operation. This allows the component to tap into whichever part of the request or response
 * lifecycle phase it needs to
 */
interface Feature {
    /**
     * Install the feature to the [SdkHttpOperation]. This allows the feature to wire itself up to the underlying
     * operation (e.g. install interceptors for various phases of execution, etc).
     */
    fun <I, O> install(operation: SdkHttpOperation<I, O>)
}
