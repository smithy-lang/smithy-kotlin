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
package software.aws.clientrt.http.request

import software.aws.clientrt.http.HeadersBuilder
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.UrlBuilder

/**
 * Used to construct an HTTP request
 */
class HttpRequestBuilder {
    /**
     * The HTTP method (verb) to use when making the request
     */
    var method: HttpMethod = HttpMethod.GET

    /**
     * Endpoint to make request to
     */
    val url: UrlBuilder = UrlBuilder()

    /**
     * HTTP headers
     */
    val headers: HeadersBuilder = HeadersBuilder()

    /**
     * Outgoing payload. Initially empty
     */
    var body: HttpBody = HttpBody.Empty

    fun build(): HttpRequest = HttpRequest(method, url.build(), headers.build(), body)
}

// convenience extensions

/**
 * Modify the URL inside the block
 */
fun HttpRequestBuilder.url(block: UrlBuilder.() -> Unit) = url.apply(block)

/**
 * Modify the headers inside the given block
 */
fun HttpRequestBuilder.headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

/**
 * Add a single header. This will append to any existing headers with the same name.
 */
fun HttpRequestBuilder.header(name: String, value: String) = headers.append(name, value)
