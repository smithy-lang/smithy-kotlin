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
    private val defaultQueryParams = builder.url.parameters.build()

    companion object Feature : HttpClientFeatureFactory<HttpRequestBuilder, DefaultRequest> {
        override val key: FeatureKey<DefaultRequest> = FeatureKey("DefaultRequest")
        override fun create(block: HttpRequestBuilder.() -> Unit): DefaultRequest {
            val builder = HttpRequestBuilder().apply(block)
            return DefaultRequest(builder)
        }
    }

    override fun install(client: SdkHttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            if (subject.url.host.isEmpty()) subject.url.host = builder.url.host
            if (subject.url.port == null) subject.url.port = builder.url.port

            subject.url.parameters.appendMissing(defaultQueryParams)

            if (subject.url.fragment == null) subject.url.fragment = builder.url.fragment
            if (subject.url.userInfo == null) subject.url.userInfo = builder.url.userInfo

            subject.headers.appendMissing(defaultHeaders)

            // FIXME - We have no way of knowing when to override these. We ought to probably change the config type
            // from `HttpRequestBuilder` to a more restricted interface of the things that can actually be defaulted
            // subject.url.scheme = builder.url.scheme
            // subject.url.forceQuery = builder.url.forceQuery
            // subject.method = builder.method
        }
    }
}
