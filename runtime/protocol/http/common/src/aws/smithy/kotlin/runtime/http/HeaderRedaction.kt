/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

/**
 * Commonly-sensitive HTTP headers that users may choose to redact from debug logging.
 * This set is NOT applied by default — users must explicitly opt in via the
 * `logRedactedHeaders` client config property.
 */
public object SensitiveHeaders {
    public val Default: Set<String> = setOf(
        "Authorization",
        "X-Amz-Security-Token",
    )
}
