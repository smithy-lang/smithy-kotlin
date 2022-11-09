/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.endpoints

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.SdkHttpRequest
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.net.Host

/**
 * Represents the endpoint a service client should make API operation calls to.
 *
 * The SDK will automatically resolve these endpoints per API client using an internal resolver.
 *
 * @property uri The base URL endpoint clients will use to make API calls to e.g. "api.myservice.com".
 * NOTE: Only `scheme`, `port`, `host` `path`, and `parameters` are valid. Other URL elements are ignored.
 *
 * @property headers A map of additional HTTP headers to be set when making calls against this endpoint.
 *
 * @property attributes A grab-bag property map of endpoint attributes. The values here are only set when an endpoint is
 * returned from evaluating a ruleset.
 *
 * If the hostname is mutable the SDK clients may modify any part of the hostname based
 * on the requirements of the API (e.g. adding or removing content in the hostname).
 *
 * As an example Amazon S3 Client prefixing "bucketname" to the hostname or changing th hostname
 * service name component from "s3" to "s3-accespoint.dualstack." requires mutable hostnames.
 *
 * Care should be taken when setting this flag and providing a custom endpoint. If the hostname
 * is expected to be mutable and the client cannot modify the endpoint correctly, the operation
 * will likely fail.
 */
public data class Endpoint @InternalApi constructor(
    public val uri: Url,
    public val headers: Headers = Headers.Empty,
    @InternalApi
    public val attributes: Attributes = Attributes(),
) {
    public constructor(uri: String) : this(Url.parse(uri))

    public constructor(
        uri: Url,
        headers: Headers = Headers.Empty,
    ) : this(uri, headers, Attributes())

    override fun equals(other: Any?): Boolean =
        other is Endpoint &&
            uri == other.uri &&
            headers == other.headers &&
            attributesEqual(other)

    private fun attributesEqual(other: Endpoint): Boolean =
        attributes.keys.size == other.attributes.keys.size &&
            attributes.keys.all {
                @Suppress("UNCHECKED_CAST")
                attributes.contains(it) && attributes.getOrNull(it as AttributeKey<Any>) == other.attributes.getOrNull(it)
            }
}

/**
 * Update an existing request with a resolved endpoint.
 *
 * Any values serialized to the HTTP path or query string are preserved (in the case of path, the existing serialized one
 * is appended to what was resolved).
 */
@InternalApi
public fun setResolvedEndpoint(req: SdkHttpRequest, endpoint: Endpoint) {
    val hostPrefix = req.context.getOrNull(HttpOperationContext.HostPrefix) ?: ""
    val hostname = "$hostPrefix${endpoint.uri.host}"
    val joinedPath = buildString {
        append(endpoint.uri.path.removeSuffix("/"))
        if (req.subject.url.path.isNotBlank()) {
            append("/")
            append(req.subject.url.path.removePrefix("/"))
        }
    }

    req.subject.url.scheme = endpoint.uri.scheme
    req.subject.url.userInfo = endpoint.uri.userInfo
    req.subject.url.host = Host.parse(hostname)
    req.subject.url.port = endpoint.uri.port
    req.subject.url.path = joinedPath
    req.subject.url.parameters.appendAll(endpoint.uri.parameters)
    req.subject.url.fragment = endpoint.uri.fragment

    req.subject.headers["Host"] = hostname
    req.subject.headers.appendAll(endpoint.headers)
}
