/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.request

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.Url

/**
 * Immutable representation of an HTTP request
 */
data class HttpRequest(
    val method: HttpMethod,
    val url: Url,
    val headers: Headers,
    val body: HttpBody
) {
    companion object {
        operator fun invoke(block: HttpRequestBuilder.() -> Unit): HttpRequest = HttpRequestBuilder().apply(block).build()
    }
}
