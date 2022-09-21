/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
// This package implements common standard library functions used by endpoint resolvers.
package aws.smithy.kotlin.runtime.http.endpoints.functions

import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.net.*
import aws.smithy.kotlin.runtime.util.text.ensureSuffix
import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent

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
public fun uriEncode(value: String): String = value.urlEncodeComponent(formUrlEncode = false)

@InternalApi
public fun parseUrl(value: String?): Url? =
    value?.let {
        val sdkUrl: aws.smithy.kotlin.runtime.http.Url
        try {
            sdkUrl = aws.smithy.kotlin.runtime.http.Url.parse(value)
        } catch (e: Exception) {
            return null
        }

        val authority = buildString {
            append(sdkUrl.host.toUrlString())
            if (sdkUrl.port != sdkUrl.scheme.defaultPort) {
                append(":${sdkUrl.port}")
            }
        }

        return Url(
            scheme = sdkUrl.scheme.protocolName,
            authority,
            path = sdkUrl.path,
            normalizedPath = sdkUrl.path.ensureSuffix("/"),
            isIp = sdkUrl.host is Host.IPv4 || sdkUrl.host is Host.IPv6,
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

// the number of top-level components an arn contains (separated by colons)
private const val ARN_COMPONENT_COUNT = 6

/**
 * Identifies the partition for the given AWS region.
 */
@InternalApi
public fun partition(partitions: List<Partition>, region: String?): PartitionConfig? =
    region?.let {
        val explicitMatch = partitions.find { it.regions.contains(region) }
        if (explicitMatch != null) {
            return explicitMatch.baseConfig.mergeWith(explicitMatch.regions[region]!!)
        }

        val fallbackMatch = partitions.find { region.matches(it.regionRegex) }
            ?: partitions.find { it.id == "aws" }
        fallbackMatch?.baseConfig
    }

@InternalApi
/**
 * Splits an ARN into its component parts.
 *
 * The resource identifier is further split based on the type or scope delimiter present (if any).
 */
public fun parseArn(value: String): Arn? {
    val split = value.split(':', limit = ARN_COMPONENT_COUNT)
    if (split[0] != "arn") return null
    if (split.size != ARN_COMPONENT_COUNT) return null

    return Arn(
        split[1],
        split[2],
        split[3],
        split[4],
        split[5].split(':', '/'),
    )
}

/**
 * Evaluates whether a string is a DNS-compatible bucket name that can be used with virtual hosted-style addressing.
 */
@InternalApi
public fun isVirtualHostableS3Bucket(value: String?, allowSubdomains: Boolean): Boolean =
    value?.let {
        if (!isValidHostLabel(value, allowSubdomains)) {
            return false
        }

        if (!allowSubdomains) {
            value.isVirtualHostableS3Segment()
        } else {
            value.split('.').all(String::isVirtualHostableS3Segment)
        }
    } ?: false

private fun String.isVirtualHostableS3Segment(): Boolean =
    length in 3..63 && none { it in 'A'..'Z' } && !isIpv4() && !isIpv6()

/**
 * A partition defines a broader set of AWS regions.
 */
@InternalApi
public data class Partition(
    public val id: String,
    /**
     * A mapping of known regions within this partition to region-specific configuration values.
     */
    public val regions: Map<String, PartitionConfig>,
    /**
     * A regular expression that can be used to identify arbitrary regions as part of this partition.
     */
    public val regionRegex: Regex,
    /**
     * The default configuration for this partition. Region-specific values in the [regions] map, if present, will
     * override these values when an explicit match is found during partitioning.
     */
    public val baseConfig: PartitionConfig,
)

/**
 * The core configuration details for a partition. This is the structure that endpoint providers interface receive as
 * the result of a partition call.
 */
@InternalApi
public data class PartitionConfig(
    public val name: String? = null,
    public val dnsSuffix: String? = null,
    public val dualStackDnsSuffix: String? = null,
    public val supportsFIPS: Boolean? = null,
    public val supportsDualStack: Boolean? = null,
) {
    public fun mergeWith(other: PartitionConfig): PartitionConfig =
        PartitionConfig(
            other.name ?: name,
            other.dnsSuffix ?: dnsSuffix,
            other.dualStackDnsSuffix ?: dualStackDnsSuffix,
            other.supportsFIPS ?: supportsFIPS,
            other.supportsDualStack ?: supportsDualStack,
        )
}

/**
 * Represents a parsed form of an ARN (Amazon Resource Name).
 */
@InternalApi
public data class Arn(
    public val partition: String,
    public val service: String,
    public val region: String,
    public val accountId: String,
    public val resourceId: List<String>,
)
