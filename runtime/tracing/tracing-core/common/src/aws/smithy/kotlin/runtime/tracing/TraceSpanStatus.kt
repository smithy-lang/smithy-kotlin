/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

/**
 * The status attached to a span.
 */
public enum class TraceSpanStatus {
    /**
     * Default status, collectors can set final status.
     */
    UNSET,

    /**
     * Span executed without error
     */
    OK,

    /**
     * An error was encountered in this span
     */
    ERROR,
}
