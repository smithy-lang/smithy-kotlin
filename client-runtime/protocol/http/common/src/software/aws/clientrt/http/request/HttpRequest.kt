/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.request

import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.Url

/**
 * Immutable representation of an HTTP request
 */
data class HttpRequest(
    val method: HttpMethod,
    val url: Url,
    val headers: Headers,
    val body: HttpBody
)
