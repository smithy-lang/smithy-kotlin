/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Entry point for creating [TraceSpan] instances.
 */
public interface Tracer {
    // TODO - do we want to be able to explicitly say "no parent", e.g. Context.root()

    /**
     * Creates a new span and makes it active.
     *
     * @param name the name of the span
     * @param parentContext the parent context to use for this span, if not set it is
     * implementation defined.
     */
    public fun createSpan(
        name: String,
        parentContext: Context? = null,
        initialAttributes: Attributes = emptyAttributes(),
        spanKind: SpanKind = SpanKind.INTERNAL,
    ): TraceSpan
}
