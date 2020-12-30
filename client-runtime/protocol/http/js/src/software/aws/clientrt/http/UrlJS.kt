/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import org.w3c.dom.url.URL
import software.aws.clientrt.http.util.encodeUrlPath
import software.aws.clientrt.http.util.splitAsQueryParameters

internal actual fun platformUrlParse(url: String): Url {
    val uri = URL(url)
    return UrlBuilder {
        scheme = Protocol.parse(uri.protocol.removeSuffix(":"))
        host = uri.hostname
        port = uri.port.toIntOrNull()?.takeIf { it > 0 }
        val trimmedPath = uri.pathname.removePrefix("/")
        if (trimmedPath.isNotBlank()) {
            path = trimmedPath.encodeUrlPath()
        }
        if (uri.username.isNotBlank()) {
            userInfo = UserInfo(uri.username, uri.password)
        }
        if (uri.search.isNotBlank()) {
            parameters.appendAll(uri.search.removePrefix("?").splitAsQueryParameters())
        }
        if (uri.hash.isNotBlank()) {
            fragment = uri.hash.removePrefix("#")
        }
    }
}