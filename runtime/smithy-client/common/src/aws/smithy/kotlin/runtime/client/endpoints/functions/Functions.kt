/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
// This package implements common standard library functions used by endpoint resolvers.
package aws.smithy.kotlin.runtime.client.endpoints.functions

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.isValidHostname
import aws.smithy.kotlin.runtime.net.toUrlString
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding
import aws.smithy.kotlin.runtime.text.ensureSuffix
import aws.smithy.kotlin.runtime.net.url.Url as SdkUrl

@InternalApi
public fun substring(value: String?, start: Int, stop: Int, reverse: Boolean): String? =
    value?.let {
        when {
            start >= stop || stop > value.length -> null
            reverse -> value.substring(value.length - stop until value.length - start)
            else -> value.substring(start until stop)
        }
    }

@InternalApi
public fun isValidHostLabel(value: String?, allowSubdomains: Boolean): Boolean =
    value?.let {
        if (!allowSubdomains) value.isValidHostname() else value.split('.').all(String::isValidHostname)
    } ?: false

@InternalApi
public fun uriEncode(value: String): String = PercentEncoding.SmithyLabel.encode(value)

@InternalApi
public fun parseUrl(value: String?): Url? =
    value?.let {
        val sdkUrl: SdkUrl
        try {
            sdkUrl = SdkUrl.parse(value)
        } catch (e: Exception) {
            return null
        }

        val authority = buildString {
            append(sdkUrl.host.toUrlString())
            if (sdkUrl.port != sdkUrl.scheme.defaultPort) {
                append(":${sdkUrl.port}")
            }
        }

        val sdkUrlPath = sdkUrl.path.toString()
        return Url(
            scheme = sdkUrl.scheme.protocolName,
            authority,
            path = sdkUrlPath,
            normalizedPath = sdkUrlPath.ensureSuffix("/"),
            isIp = sdkUrl.host is Host.IpAddress,
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
