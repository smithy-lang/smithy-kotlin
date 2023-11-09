/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.text.encodeUrlPath
import aws.smithy.kotlin.runtime.text.urlDecodeComponent
import aws.smithy.kotlin.runtime.text.urlEncodeComponent
import aws.smithy.kotlin.runtime.util.CanDeepCopy

/**
 * Represents an immutable URL of the form: `scheme://[userinfo@]host[:port][/path][?query][#fragment]`
 *
 * @property scheme The wire protocol (e.g. http, https, ws, wss, etc)
 * @property host hostname
 * @property port port to connect to the host on, defaults to [Scheme.defaultPort]
 * @property path (raw) path without the query
 * @property parameters (raw) query parameters
 * @property fragment (raw) URL fragment
 * @property userInfo username and password (optional)
 * @property forceQuery keep trailing question mark regardless of whether there are any query parameters
 * @property encodeParameters configures if parameter values are encoded (default) or left as-is.
 */
public data class Url(
    public val scheme: Scheme,
    public val host: Host,
    public val port: Int = scheme.defaultPort,
    public val path: String = "",
    public val parameters: QueryParameters = QueryParameters.Empty,
    public val fragment: String? = null,
    public val userInfo: UserInfo? = null,
    public val forceQuery: Boolean = false,
    public val encodeParameters: Boolean = true,
) {
    init {
        require(port in 1..65535) { "Given port $port is not in required range [1, 65535]" }
    }

    public companion object {
        public fun parse(url: String): Url = parse(url, UrlDecoding.DecodeAll)
        public fun parse(url: String, decodingBehavior: UrlDecoding): Url = urlParseImpl(url, decodingBehavior)
    }

    override fun toString(): String = buildString {
        append(scheme.protocolName)
        append("://")
        userInfo?.let { userinfo ->
            if (userinfo.username.isNotBlank()) {
                append(userinfo.username.urlEncodeComponent())
                if (userinfo.password.isNotBlank()) {
                    append(":${userinfo.password.urlEncodeComponent()}")
                }
                append("@")
            }
        }

        append(host.toUrlString())
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
    encodeParameters: Boolean = true,
): String = buildString {
    if (path.isNotBlank()) {
        append("/")
        append(path.removePrefix("/").encodeUrlPath())
    }

    if (!queryParameters.isNullOrEmpty() || forceQuery) {
        append("?")
    }

    if (encodeParameters) {
        queryParameters?.let { urlEncodeQueryParametersTo(it, this) }
    } else {
        queryParameters?.let { urlEncodeQueryParametersTo(it, this, encodeFn = { param -> param }) }
    }

    if (!fragment.isNullOrBlank()) {
        append("#")
        append(fragment.urlEncodeComponent())
    }
}

/**
 * URL username and password
 */
public data class UserInfo(public val username: String, public val password: String)

/**
 * Construct a URL by its individual components
 */
public class UrlBuilder : CanDeepCopy<UrlBuilder> {
    public var scheme: Scheme = Scheme.HTTPS
    public var host: Host = Host.Domain("")
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
        forceQuery,
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

@InternalApi
public val UrlBuilder.encodedPath: String
    get() = encodePath(path, parameters.entries(), fragment, forceQuery)

/**
 * Constructs a [UserInfo] from its %-encoded representation.
 */
@InternalApi
public fun UserInfo(value: String): UserInfo {
    val info = value.split(":")
    return UserInfo(
        info[0].urlDecodeComponent(),
        if (info.size > 1) info[1].urlDecodeComponent() else "",
    )
}
