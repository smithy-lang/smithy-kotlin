/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

/**
 * Represents an immutable URL of the form: `[scheme:][//[userinfo@]host][/]path[?query][#fragment]`
 *
 * @property scheme The wire protocol (e.g. http, https, ws, wss, etc)
 * @property host hostname
 * @property port port to connect to the host on, defaults to [Protocol.defaultPort]
 * @property path (raw) path without the query
 * @property parameters (raw) query parameters
 * @property fragment URL fragment
 * @property userInfo username and pasword (optional)
 * @property forceQuery keep trailing question mark regardless of whether there are any query parameters
 */
data class Url(
    val scheme: Protocol,
    val host: String,
    val port: Int = scheme.defaultPort,
    val path: String = "",
    val parameters: QueryParameters? = null,
    val fragment: String? = null,
    val userInfo: UserInfo? = null,
    val forceQuery: Boolean = false
) {
    init {
        require(port in 1..65536) { "port must be in between 1 and 65536" }
    }

    override fun toString(): String = buildString {
        // FIXME - the userinfo, path, and fragment are raw at this point and need escaped as well probably
        append(scheme.protocolName)
        append("://")
        userInfo?.let { userinfo ->
            if (userinfo.username.isNotBlank()) {
                append(userinfo.username)
                if (userinfo.password.isNotBlank()) {
                    append(":${userinfo.password}")
                }
                append("@")
            }
        }

        append(host)
        if (port != scheme.defaultPort) {
            append(":$port")
        }

        if (path.isNotBlank()) {
            append("/")
            append(path.removePrefix("/"))
        }

        if ((parameters != null && !parameters.isEmpty()) || forceQuery) {
            append("?")
        }
        parameters?.urlEncodeTo(this)

        if (fragment != null && fragment.isNotBlank()) {
            append("#")
            append(fragment)
        }
    }
}

/**
 * URL username and password
 */
data class UserInfo(val username: String, val password: String)

/**
 * Construct a URL by it's individual components
 */
class UrlBuilder {
    var scheme = Protocol.HTTPS
    var host: String = ""
    var port: Int? = null
    var path: String = ""
    var parameters: QueryParametersBuilder = QueryParametersBuilder()
    var fragment: String? = null
    var userInfo: UserInfo? = null
    var forceQuery: Boolean = false

    companion object {
        operator fun invoke(block: UrlBuilder.() -> Unit): Url = UrlBuilder().apply(block).build()
    }

    fun build(): Url = Url(
        scheme,
        host,
        port ?: scheme.defaultPort,
        path,
        if (parameters.isEmpty()) null else parameters.build(),
        fragment,
        userInfo,
        forceQuery)
}

fun UrlBuilder.parameters(block: QueryParametersBuilder.() -> Unit) = parameters.apply(block)
