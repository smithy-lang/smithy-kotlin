/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.newnet

import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.net.splitHostPort
import aws.smithy.kotlin.runtime.net.toUrlString
import aws.smithy.kotlin.runtime.util.text.Scanner
import aws.smithy.kotlin.runtime.util.text.encoding.Encodable
import aws.smithy.kotlin.runtime.util.text.encoding.Encoding

/**
 * Represents a full, valid URL
 * @param scheme The wire protocol (e.g., http, https, etc.)
 * @param host The [Host] for the URL
 * @param port The remote port number for the URL (e.g., TCP port)
 * @param path The path element of this URL
 * @param queryParameters Optional query parameters for this URL. Note that `null` parameters are different from *empty*
 * parameters.
 * @param fragment Optional fragment component of this URL (without the `#` character)
 * @param userInfo Optional user authentication information for this URL
 */
public class Url private constructor(
    public val scheme: Scheme,
    public val host: Host,
    public val port: Int,
    public val path: UrlPath,
    public val queryParameters: QueryParameters?,
    public val fragment: Encodable?,
    public val userInfo: UserInfo?,
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
        public fun parseEncoded(encoded: String): Url = Url {
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
                    queryParameters { parseEncoded(it) }
                }
            }

            scanner.ifStartsWith("#") {
                scanner.upToOrEnd { fragmentEncoded = it }
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
        queryParameters?.let {
            append('?')
            append(it)
        }
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

        private val path: UrlPath.Builder = url?.path?.toBuilder() ?: UrlPath.Builder()

        /**
         * Update the [UrlPath] of this URL via a DSL builder block
         * @param block The code to apply to the [UrlPath] builder
         */
        public fun path(block: UrlPath.Builder.() -> Unit) {
            path.apply(block)
        }

        /**
         * Get or set the URL path as a **decoded** string
         */
        public var pathDecoded: String
            get() = path.asDecoded()
            set(value) { path.parseDecoded(value) }

        /**
         * Get or set the URL path as an **encoded** string
         */
        public var pathEncoded: String
            get() = path.asEncoded()
            set(value) { path.parseEncoded(value) }

        // Query parameters

        private var queryParameters = url?.queryParameters?.toBuilder()

        /**
         * Remove all query parameters from this URL
         */
        public fun clearQueryParameters() {
            queryParameters = null
        }

        /**
         * Update the [QueryParameters] of this URL via a DSL builder block
         * @param block The code to apply to the [QueryParameters] builder
         */
        public fun queryParameters(block: QueryParameters.Builder.() -> Unit) {
            val queryParameters = this.queryParameters ?: QueryParameters.Builder().also { this.queryParameters = it }
            queryParameters.apply(block)
        }

        /**
         * Get or set the query parameters as a **decoded** string
         */
        public var queryParametersDecoded: String?
            get() = queryParameters?.asDecoded()
            set(value) {
                if (value == null) {
                    queryParameters = null
                } else {
                    queryParameters { parseDecoded(value) }
                }
            }

        /**
         * Get or set the query parameters as an **encoded** string
         */
        public var queryParametersEncoded: String?
            get() = queryParameters?.asEncoded()
            set(value) {
                if (value == null) {
                    queryParameters = null
                } else {
                    queryParameters { parseEncoded(value) }
                }
            }

        // Fragment

        private var fragment: Encodable? = url?.fragment

        /**
         * Get or set the fragment as a **decoded** string
         */
        public var fragmentDecoded: String?
            get() = fragment?.decoded
            set(value) { fragment = value?.let(Encoding.Fragment::encodableFromDecoded) }

        /**
         * Get or set the fragment as an **encoded** string
         */
        public var fragmentEncoded: String?
            get() = fragment?.encoded
            set(value) { fragment = value?.let(Encoding.Fragment::encodableFromEncoded) }

        // User info

        private var userInfo: UserInfo.Builder? = url?.userInfo?.toBuilder()

        /**
         * Remove the user info from the URL
         */
        public fun clearUserInfo() {
            userInfo = null
        }

        /**
         * Set the user info in this URL via a DSL builder block
         * @param block The code to apply to the [UserInfo] builder
         */
        public fun userInfo(block: UserInfo.Builder.() -> Unit) {
            val userInfo = this.userInfo ?: UserInfo.Builder().also { this.userInfo = it }
            userInfo.apply(block)
        }

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
            queryParameters?.build(),
            fragment,
            userInfo?.build(),
        )
    }
}
