/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.collections.AttributeKey

/**
 * HTTP headers that are potentially sensitive and may be redacted from debug logging.
 * This set is NOT applied by default — users must explicitly opt in via the
 * `logRedactedHeaders` client config property.
 *
 * Example usage:
 * ```kotlin
 * val client = MyServiceClient {
 *     logRedactedHeaders += PotentiallySensitiveHeaders.Default
 * }
 * ```
 */
public object PotentiallySensitiveHeaders {
    /**
     * The default set of potentially sensitive headers, including:
     * - `Authorization`
     * - `X-Amz-Security-Token`
     */
    public val Default: Set<String> = setOf(
        "Authorization",
        "X-Amz-Security-Token",
    )
}

/**
 * The string used to replace sensitive header values in debug logging output.
 */
internal const val SENSITIVE_DATA_REDACTED = "*** Sensitive Data Redacted ***"

internal val LOG_REDACTED_HEADERS_KEY = AttributeKey<Set<String>>("aws.smithy.kotlin#LogRedactedHeaders")
