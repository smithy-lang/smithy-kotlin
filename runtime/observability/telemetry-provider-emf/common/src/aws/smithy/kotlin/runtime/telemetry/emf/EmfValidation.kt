/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

/**
 * Truncates this string to [maxLength] characters, warning if truncation was necessary.
 *
 * EMF constraint violations encountered while recording a metric are corrected rather than thrown so
 * that emitting telemetry never fails the operation being measured. [description] identifies the
 * offending field in the warning.
 */
internal fun String.truncateTo(maxLength: Int, description: String): String = if (length <= maxLength) {
    this
} else {
    emfLog("[WARN] EMF $description exceeds $maxLength characters and was truncated: '$this'")
    take(maxLength)
}
