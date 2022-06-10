/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.util.splitAsQueryParameters
import aws.smithy.kotlin.runtime.util.InternalApi
import java.net.URI

internal actual fun platformUrlParse(url: String): Url {
    val uri = URI.create(url)
    return uri.toUrl()
}

/**
 * Convert a [java.net.URI] to a [Url]
 */
@InternalApi
fun URI.toUrl(): Url {
    val uri = this
    return UrlBuilder {
        scheme = Protocol.parse(uri.scheme)
        host = uri.host
        if (uri.port > 0) {
            port = uri.port
        }
        path = uri.path

        if (uri.query != null && uri.query.isNotBlank()) {
            val parsedParameters = uri.query.splitAsQueryParameters()
            parameters.appendAll(parsedParameters)
        }

        if (uri.userInfo != null && uri.userInfo.isNotBlank()) {
            val userInfoParts = uri.userInfo.split(":")
            val user = userInfoParts[0]
            val pw = if (userInfoParts.size > 1) userInfoParts[1] else ""
            userInfo = UserInfo(user, pw)
        }

        if (uri.fragment != null && uri.fragment.isNotBlank()) fragment = uri.fragment
    }
}
