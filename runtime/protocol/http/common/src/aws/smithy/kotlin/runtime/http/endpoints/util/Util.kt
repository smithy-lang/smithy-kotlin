/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.endpoints.util

import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.net.isIpv4
import aws.smithy.kotlin.runtime.util.net.isIpv6
import aws.smithy.kotlin.runtime.util.net.isValidHostname
import aws.smithy.kotlin.runtime.util.text.ensureSuffix
import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent

@InternalApi
public fun isSet(value: Any?): Boolean = value != null

@InternalApi
public fun not(value: Boolean): Boolean = !value

@InternalApi
public fun substring(value: String, start: Int, stop: Int, reverse: Boolean): String? =
    when {
        start >= stop || stop > value.length -> null
        reverse -> value.substring(value.length - stop until value.length - start)
        else -> value.substring(start until stop)
    }

@InternalApi
public fun stringEquals(i: String, j: String): Boolean = i == j

@InternalApi
public fun isValidHostLabel(value: String, allowSubdomains: Boolean): Boolean =
    if (allowSubdomains) value.isValidHostname() else value.split('.').all(String::isValidHostname)

@InternalApi
public fun uriEncode(value: String): String = value.urlEncodeComponent(formUrlEncode = false)

@InternalApi
public fun booleanEquals(i: Boolean, j: Boolean): Boolean = i == j

@InternalApi
public fun parseUrl(value: String): Url? {
    val sdkUrl: aws.smithy.kotlin.runtime.http.Url
    try {
        sdkUrl = aws.smithy.kotlin.runtime.http.Url.parse(value)
    } catch (e: Exception) {
        return null
    }

    val isIpv6 = sdkUrl.host.isIpv6()
    val authority = buildString {
        append(if (isIpv6) "[${sdkUrl.host}]" else sdkUrl.host)
        if (sdkUrl.port != sdkUrl.scheme.defaultPort) {
            append(":${sdkUrl.port}")
        }
    }

    return Url(
        scheme = sdkUrl.scheme.protocolName,
        authority,
        path = sdkUrl.path,
        normalizedPath = sdkUrl.path.ensureSuffix("/"),
        isIp = isIpv6 || sdkUrl.host.isIpv4(),
    )
}

@InternalApi
public data class Url(
    public val scheme: String,
    public val authority: String,
    public val path: String,
    public val normalizedPath: String,
    public val isIp: Boolean,
)
