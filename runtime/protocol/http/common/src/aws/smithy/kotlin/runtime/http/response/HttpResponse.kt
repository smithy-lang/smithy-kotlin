/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.ProtocolResponse
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode

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
