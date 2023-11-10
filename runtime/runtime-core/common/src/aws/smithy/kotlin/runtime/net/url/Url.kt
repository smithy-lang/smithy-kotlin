/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.net.isIpv6
import aws.smithy.kotlin.runtime.net.toUrlString
import aws.smithy.kotlin.runtime.text.Scanner
import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding
import aws.smithy.kotlin.runtime.text.urlDecodeComponent

/**
 * Represents a full, valid URL
 * @param scheme The wire protocol (e.g., http, https, etc.)
 * @param host The [Host] for the URL
 * @param port The remote port number for the URL (e.g., TCP port)
 * @param path The path element of this URL
 * @param parameters The query parameters for this URL.
 * @param fragment Optional fragment component of this URL (without the `#` character)
 * @param userInfo Optional user authentication information for this URL
 */
public class Url private constructor(
    public val scheme: Scheme,
    public val host: Host,
    public val port: Int,
    public val path: UrlPath,
    public val parameters: QueryParameters,
    public val userInfo: UserInfo,
    public val fragment: Encodable?,
) {
    public companion object {
        /**
         * Create a new [Url] via a DSL builder block
         * @param block The code to apply to the builder
         * @return A new [Url] instance
         */
        public operator fun invoke(block: Builder.() -> Unit): Url = Builder().apply(block).build()

        /**
         * Parse an **encoded** string into a [Url] instance
         * @param encoded An encoded URL string
         * @return A new [Url] instance
         */
        public fun parse(encoded: String): Url = Url {
            val scanner = Scanner(encoded)
            scanner.requireAndSkip("://") { scheme = Scheme.parse(it) }

            scanner.optionalAndSkip("@") {
                userInfo { parseEncoded(it) }
            }

            scanner.upToOrEnd("/", "?", "#") { authority ->
                val (h, p) = authority.splitHostPort()
                host = h
                p?.let { port = it }
            }

            scanner.ifStartsWith("/") {
                scanner.upToOrEnd("?", "#") {
                    path { parseEncoded("/$it") }
                }
            }

            scanner.ifStartsWith("?") {
                scanner.upToOrEnd("#") {
                    parameters { parseEncoded(it) }
                }
            }

            scanner.ifStartsWith("#") {
                scanner.upToOrEnd { encodedFragment = it }
            }
        }
    }

    init {
        require(port in 1..65535) { "Given port $port is not in required range [1, 65535]" }
    }

    /**
     * Copy the properties of this [Url] instance into a new [Builder] object. Any changes to the builder *will not*
     * affect this instance.
     */
    public fun toBuilder(): Builder = Builder(this)

    /**
     * Returns the encoded string representation of this URL
     */
    override fun toString(): String = buildString {
        append(scheme.protocolName)
        append("://")
        append(host.toUrlString())
        if (port != scheme.defaultPort) {
            append(":")
            append(port)
        }
        append(path)
        append(parameters)
        fragment?.let {
            append('#')
            append(it.encoded)
        }
    }

    /**
     * A mutable builder used to construct [Url] instances
     */
    public class Builder internal constructor(url: Url?) {
        /**
         * Initialize an empty [Url] builder
         */
        public constructor() : this(null)

        // Simple fields

        /**
         * The wire protocol (e.g., http, https, etc.)
         */
        public var scheme: Scheme = url?.scheme ?: Scheme.HTTPS

        /**
         * The [Host] for the URL
         */
        public var host: Host = url?.host ?: Host.Domain("")

        /**
         * The remote port number for the URL (e.g., TCP port)
         */
        public var port: Int? = url?.port

        // Path

        /**
         * Gets the URL path builder
         */
        public val path: UrlPath.Builder = url?.path?.toBuilder() ?: UrlPath.Builder()

        /**
         * Update the [UrlPath] of this URL via a DSL builder block
         * @param block The code to apply to the [UrlPath] builder
         */
        public fun path(block: UrlPath.Builder.() -> Unit) {
            path.apply(block)
        }

        // Query parameters

        /**
         * Gets the query parameters builder
         */
        public val parameters: QueryParameters.Builder = url?.parameters?.toBuilder() ?: QueryParameters.Builder()

        /**
         * Update the [QueryParameters] of this URL via a DSL builder block
         * @param block The code to apply to the [QueryParameters] builder
         */
        public fun parameters(block: QueryParameters.Builder.() -> Unit) {
            parameters.apply(block)
        }

        // User info

        /**
         * Get the user info builder
         */
        public val userInfo: UserInfo.Builder = url?.userInfo?.toBuilder() ?: UserInfo.Builder()

        /**
         * Set the user info in this URL via a DSL builder block
         * @param block The code to apply to the [UserInfo] builder
         */
        public fun userInfo(block: UserInfo.Builder.() -> Unit) {
            userInfo.apply(block)
        }

        // Fragment

        private var fragment: Encodable? = url?.fragment

        /**
         * Get or set the fragment as a **decoded** string
         */
        public var decodedFragment: String?
            get() = fragment?.decoded
            set(value) { fragment = value?.let(PercentEncoding.Fragment::encodableFromDecoded) }

        /**
         * Get or set the fragment as an **encoded** string
         */
        public var encodedFragment: String?
            get() = fragment?.encoded
            set(value) { fragment = value?.let(PercentEncoding.Fragment::encodableFromEncoded) }

        // Build method

        /**
         * Build a new [Url] from the currently-configured builder values
         * @return A new [Url] instance
         */
        public fun build(): Url = Url(
            scheme,
            host,
            port ?: scheme.defaultPort,
            path.build(),
            parameters.build(),
            userInfo.build(),
            fragment,
        )
    }
}

private fun String.splitHostPort(): Pair<Host, Int?> {
    val lBracketIndex = indexOf('[')
    val rBracketIndex = indexOf(']')
    val lastColonIndex = lastIndexOf(":")
    val hostEndIndex = when {
        rBracketIndex != -1 -> rBracketIndex + 1
        lastColonIndex != -1 -> lastColonIndex
        else -> length
    }

    require(lBracketIndex == -1 && rBracketIndex == -1 || lBracketIndex < rBracketIndex) { "unmatched [ or ]" }
    require(lBracketIndex <= 0) { "unexpected characters before [" }
    require(rBracketIndex == -1 || rBracketIndex == hostEndIndex - 1) { "unexpected characters after ]" }

    val host = if (lBracketIndex != -1) {
        substring(lBracketIndex + 1 until rBracketIndex)
    } else {
        substring(0 until hostEndIndex)
    }

    val decodedHost = host.urlDecodeComponent()
    if (lBracketIndex != -1 && rBracketIndex != -1 && !decodedHost.isIpv6()) {
        throw IllegalArgumentException("non-ipv6 host was enclosed in []-brackets")
    }

    return Pair(
        Host.parse(decodedHost),
        if (hostEndIndex != -1 && hostEndIndex != length) substring(hostEndIndex + 1).toInt() else null,
    )
}
