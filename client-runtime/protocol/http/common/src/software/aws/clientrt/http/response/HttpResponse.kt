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
package software.aws.clientrt.http.response

import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.request.HttpRequest

/**
 * Immutable container for an HTTP response
 *
 * @property [status] response status code
 * @property [headers] response headers
 * @property [body] response body content
 * @property [request] the original request
 */
data class HttpResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: HttpBody,
    val request: HttpRequest
) {
    // TODO - can't implement until we decide on a datetime implementation
    // val responseTime: Date
    //     get() = ...

    // val requestTime: Date
    //     get() = ...

    /**
     * Close the underlying response and cleanup any resources associated with it.
     * After closing the response body is no longer valid and should not be read from.
     */
    fun close() {
        when (body) {
            is HttpBody.Streaming -> body.readFrom().cancel(null)
            else -> return
        }
    }
}
