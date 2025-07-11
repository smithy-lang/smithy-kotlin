/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.PlatformProvider

/**
 * A [BearerTokenProvider] that extracts the bearer token from the target environment variable.
 */
public class EnvironmentBearerTokenProvider(
    private val key: String,
    private val platform: PlatformProvider = PlatformProvider.System,
) : BearerTokenProvider {
    override suspend fun resolve(attributes: Attributes): BearerToken {
        val bearerToken = platform.getenv(key)
            ?: error("$key environment variable is not set")

        return object : BearerToken {
            override val token: String = bearerToken
            override val attributes: Attributes = emptyAttributes()
            override val expiration: Instant? = null
        }
    }
}
