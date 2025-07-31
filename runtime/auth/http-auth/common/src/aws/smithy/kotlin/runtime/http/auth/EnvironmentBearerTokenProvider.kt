/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.emitBusinessMetric
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.mutableAttributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.PlatformProvider

/**
 * A [BearerTokenProvider] that extracts the bearer token from JVM system properties or environment variables.
 */
public class EnvironmentBearerTokenProvider(
    private val sysPropKey: String?,
    private val envKey: String?,
    private val platform: PlatformProvider = PlatformProvider.System,
) : BearerTokenProvider {
    @Deprecated("Use constructor with separate system property and environment variable keys")
    public constructor(
        key: String,
        platform: PlatformProvider = PlatformProvider.System,
    ) : this(null, key, platform)

    override suspend fun resolve(attributes: Attributes): BearerToken {
        val bearerToken = sysPropKey?.let { platform.getProperty(it) }
            ?: envKey?.let { platform.getenv(it) }
            ?: error("neither system property $sysPropKey nor environment variable $envKey is set")
        return object : BearerToken {
            override val token: String = bearerToken
            override val attributes: Attributes = mutableAttributes().apply {
                emitBusinessMetric(SmithyBusinessMetric.BEARER_SERVICE_ENV_VARS)
            }
            override val expiration: Instant? = null
        }
    }
}
