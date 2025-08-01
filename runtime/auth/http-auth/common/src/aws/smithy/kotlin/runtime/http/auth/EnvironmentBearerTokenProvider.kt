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
public class EnvironmentBearerTokenProvider private constructor(
    private val sysPropKey: String?,
    private val envKey: String,
    private val platform: PlatformProvider = PlatformProvider.System,
) : BearerTokenProvider {
    @Deprecated("This constructor does not support a parameter for a system property key and will be removed in version 1.6.x")
    public constructor(
        envKey: String,
        platform: PlatformProvider = PlatformProvider.System,
    ) : this(null, envKey, platform)

    public constructor(
        sysPropKey: String,
        envKey: String,
        platform: PlatformProvider = PlatformProvider.System,
        @Suppress("UNUSED_PARAMETER") dummy: Boolean = true,
    ) : this(sysPropKey as String?, envKey, platform)

    override suspend fun resolve(attributes: Attributes): BearerToken {
        val bearerToken = sysPropKey?.let(platform::getProperty) ?: platform.getenv(envKey)
        if (bearerToken.isNullOrBlank()) throw IllegalStateException("""Missing values for system property "$sysPropKey" and environment variable "$envKey"""")

        return object : BearerToken {
            override val token: String = bearerToken
            override val attributes: Attributes = mutableAttributes().apply {
                emitBusinessMetric(SmithyBusinessMetric.BEARER_SERVICE_ENV_VARS)
            }
            override val expiration: Instant? = null
        }
    }
}
