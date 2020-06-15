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
package software.aws.clientrt.http.engine

import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

/**
 * Functionality a real HTTP client must provide
 */
interface HttpClientEngine {
    /**
     * Execute a single HTTP request and return the response.
     */
    suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse

    /**
     * Shutdown and cleanup any resources
     */
    fun close() { return }

    /**
     * Install the [SdkHttpClient] into the engine
     */
    fun install(client: SdkHttpClient) { return }
}
