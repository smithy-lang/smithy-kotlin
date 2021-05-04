/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine

import software.aws.clientrt.http.SdkHttpClient
import software.aws.clientrt.http.request.HttpRequest
import software.aws.clientrt.http.response.HttpCall

/**
 * Functionality a real HTTP client must provide
 */
interface HttpClientEngine {
    /**
     * Execute a single HTTP request and return the response.
     */
    suspend fun roundTrip(request: HttpRequest): HttpCall

    /**
     * Shutdown and cleanup any resources
     */
    fun close() { /* Pass */ }

    /**
     * Install the [SdkHttpClient] into the engine
     */
    fun install(client: SdkHttpClient) { /* Pass */ }
}
