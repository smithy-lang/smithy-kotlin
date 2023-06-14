/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

/**
 * Indicates whether the operation/task represented by a span is known to be successful or not.
 */
public enum class SpanStatus {
    /**
     * Default implicit status. Most spans should be unset to let the telemetry
     * processors/samplers decide.
     */
    UNSET,

    /**
     * A span that has been validated as being successful (even in the presence of errors)
     */
    OK,

    /**
     * Indicates the operation the span represents was unsuccessful.
     */
    ERROR,
}
