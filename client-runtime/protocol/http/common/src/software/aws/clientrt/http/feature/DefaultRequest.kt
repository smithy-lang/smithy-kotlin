/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.http.Feature
import software.aws.clientrt.http.FeatureKey
import software.aws.clientrt.http.HttpClientFeatureFactory
import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.HttpRequestPipeline

/**
 * Feature that sets default values for all outgoing requests from an [SdkHttpClient]
 */
class DefaultRequest(private val builder: HttpRequestBuilder) : Feature {
    private val defaultHeaders = builder.headers.build()

    companion object Feature : HttpClientFeatureFactory<HttpRequestBuilder, DefaultRequest> {
        override val key: FeatureKey<DefaultRequest> = FeatureKey("DefaultRequest")
        override fun create(block: HttpRequestBuilder.() -> Unit): DefaultRequest {
            val builder = HttpRequestBuilder().apply(block)
            return DefaultRequest(builder)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            subject.url.host = builder.url.host
            subject.url.port = builder.url.port
            subject.url.parameters = builder.url.parameters
            subject.url.scheme = builder.url.scheme
            subject.url.forceQuery = builder.url.forceQuery
            subject.url.fragment = builder.url.fragment
            subject.url.userInfo = builder.url.userInfo
            subject.headers.appendAll(defaultHeaders)
            subject.method = builder.method
            subject.body = builder.body
        }
    }
}
