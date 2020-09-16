/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
