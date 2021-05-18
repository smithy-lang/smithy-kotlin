/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import software.aws.clientrt.http.QueryParameters
import software.aws.clientrt.http.QueryParametersBuilder
import software.aws.clientrt.util.text.splitAsQueryString

/**
 * Split a (decoded) query string "foo=baz&bar=quux" into it's component parts
 */
public fun String.splitAsQueryParameters(): QueryParameters {
    val builder = QueryParametersBuilder()
    splitAsQueryString().entries.forEach { entry ->
        builder.appendAll(entry.key, entry.value)
    }
    return builder.build()
}
