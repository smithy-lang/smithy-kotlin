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
            context.url.host = builder.url.host
            context.url.port = builder.url.port
            context.url.parameters = builder.url.parameters
            context.url.scheme = builder.url.scheme
            context.url.forceQuery = builder.url.forceQuery
            context.url.fragment = builder.url.fragment
            context.url.userInfo = builder.url.userInfo
            context.headers.appendAll(defaultHeaders)
            context.method = builder.method
            context.body = builder.body
        }
    }
}
