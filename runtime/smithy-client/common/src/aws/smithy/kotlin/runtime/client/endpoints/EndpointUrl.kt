/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.endpoints

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.net.url.Url

/**
 * Returns [endpoint] without its query, including a bare `?`. Returns [endpoint] unchanged if it has no query or isn't a
 * valid URL.
 */
@InternalApi
public fun withoutQuery(endpoint: String?): String? {
    if (endpoint == null || '?' !in endpoint) return endpoint

    val url = try {
        Url.parse(endpoint)
    } catch (e: Exception) {
        return endpoint
    }
    if (!url.hasQuery) return endpoint

    return url.toBuilder().apply {
        parameters.clear()
        parameters.forceQuerySeparator = false
    }.build().toString()
}

/**
 * True if this URL has a query. A bare `?` (`https://example.com?`) counts as an empty query.
 */
internal val Url.hasQuery: Boolean
    get() = parameters.isNotEmpty() || parameters.forceQuerySeparator
