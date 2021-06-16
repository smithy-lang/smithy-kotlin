/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.response

import software.aws.clientrt.ProtocolResponse
import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode

/**
 * Immutable container for an HTTP response
 *
 * @property [status] response status code
 * @property [headers] response headers
 * @property [body] response body content
 */
data class HttpResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: HttpBody,
) : ProtocolResponse

/**
 * Get an HTTP header value by name. Returns the first header if multiple headers are set
 */
fun ProtocolResponse.header(name: String): String? {
    val httpResp = this as? HttpResponse
    return httpResp?.headers?.get(name)
}

/**
 * Get all HTTP header values associated with the given name.
 */
fun ProtocolResponse.getAllHeaders(name: String): List<String>? {
    val httpResp = this as? HttpResponse
    return httpResp?.headers?.getAll(name)
}

/**
 * Get the HTTP status code of the response
 */
fun ProtocolResponse.statusCode(): HttpStatusCode? {
    val httpResp = this as? HttpResponse
    return httpResp?.status
}
