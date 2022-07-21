/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.util.CanDeepCopy
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.text.encodeUrlPath

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
 * @property encodeParameters configures if parameter values are encoded (default) or left as-is.
 */
public data class Url(
    public val scheme: Protocol,
    public val host: String,
    public val port: Int = scheme.defaultPort,
    public val path: String = "",
    public val parameters: QueryParameters = QueryParameters.Empty,
    public val fragment: String? = null,
    public val userInfo: UserInfo? = null,
    public val forceQuery: Boolean = false,
    public val encodeParameters: Boolean = true
) {
    init {
        require(port in 1..65536) { "port must be in between 1 and 65536" }
    }

    public companion object {
        public fun parse(url: String): Url = platformUrlParse(url)
    }

    override fun toString(): String = buildString {
        // FIXME - the userinfo and fragment are raw at this point and need escaped as well probably
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

        append(encodedPath)
    }

    /**
     * Get the full encoded path including query parameters and fragment
     */
    public val encodedPath: String
        get() = encodePath(path, parameters.entries(), fragment, forceQuery, encodeParameters)
}

// get the full encoded URL path component e.g. `/path/foo/bar?x=1&y=2#fragment`
private fun encodePath(
    path: String,
    queryParameters: Set<Map.Entry<String, List<String>>>? = null,
    fragment: String? = null,
    forceQuery: Boolean = false,
    encodeParameters: Boolean = true
): String = buildString {
    if (path.isNotBlank()) {
        append("/")
        append(path.removePrefix("/").encodeUrlPath())
    }

    if ((queryParameters != null && queryParameters.isNotEmpty()) || forceQuery) {
        append("?")
    }

    if (encodeParameters) {
        queryParameters?.let { urlEncodeQueryParametersTo(it, this) }
    } else {
        queryParameters?.let { urlEncodeQueryParametersTo(it, this, encodeFn = { param -> param }) }
    }

    if (fragment != null && fragment.isNotBlank()) {
        append("#")
        append(fragment)
    }
}

/**
 * URL username and password
 */
public data class UserInfo(public val username: String, public val password: String)

/**
 * Construct a URL by it's individual components
 */
public class UrlBuilder : CanDeepCopy<UrlBuilder> {
    public var scheme: Protocol = Protocol.HTTPS
    public var host: String = ""
    public var port: Int? = null
    public var path: String = ""
    public var parameters: QueryParametersBuilder = QueryParametersBuilder()
    public var fragment: String? = null
    public var userInfo: UserInfo? = null
    public var forceQuery: Boolean = false

    public companion object {
        public operator fun invoke(block: UrlBuilder.() -> Unit): Url = UrlBuilder().apply(block).build()
    }

    public fun build(): Url = Url(
        scheme,
        host,
        port ?: scheme.defaultPort,
        path,
        if (parameters.isEmpty()) QueryParameters.Empty else parameters.build(),
        fragment,
        userInfo,
        forceQuery
    )

    override fun deepCopy(): UrlBuilder {
        val builder = this
        return UrlBuilder().apply {
            scheme = builder.scheme
            host = builder.host
            port = builder.port
            path = builder.path
            parameters = builder.parameters.deepCopy()
            fragment = builder.fragment
            userInfo = builder.userInfo?.copy()
            forceQuery = builder.forceQuery
        }
    }

    override fun toString(): String =
        "UrlBuilder(scheme=$scheme, host='$host', port=$port, path='$path', parameters=$parameters, fragment=$fragment, userInfo=$userInfo, forceQuery=$forceQuery)"
}

public fun UrlBuilder.parameters(block: QueryParametersBuilder.() -> Unit) {
    parameters.apply(block)
}

// TODO - when we get to other platforms we will likely just roll our own - for now we are going to punt and use JVM
// capabilities to bootstrap this
internal expect fun platformUrlParse(url: String): Url

@InternalApi
public val UrlBuilder.encodedPath: String
    get() = encodePath(path, parameters.entries(), fragment, forceQuery)
