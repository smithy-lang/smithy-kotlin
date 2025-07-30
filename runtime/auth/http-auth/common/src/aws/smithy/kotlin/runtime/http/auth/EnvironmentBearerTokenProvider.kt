/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.emitBusinessMetric
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.toMutableAttributes
import aws.smithy.kotlin.runtime.time.Instant

/**
 * A [BearerTokenProvider] that extracts the bearer token from the target environment variable.
 */
public class EnvironmentBearerTokenProvider(
    private val bearerToken: String,
) : BearerTokenProvider {
    override suspend fun resolve(attributes: Attributes): BearerToken = object : BearerToken {
        override val token: String = bearerToken
        override val attributes: Attributes = attributes.toMutableAttributes().apply {
            emitBusinessMetric(SmithyBusinessMetric.BEARER_SERVICE_ENV_VARS)
        }
        override val expiration: Instant? = null
    }
}
