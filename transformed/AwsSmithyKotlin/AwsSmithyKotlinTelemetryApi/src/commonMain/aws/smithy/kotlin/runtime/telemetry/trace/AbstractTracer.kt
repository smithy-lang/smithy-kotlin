/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context

/**
 * An abstract implementation of a tracer. By default, this class uses no-op implementations for all members unless
 * overridden in a subclass.
 */
public abstract class AbstractTracer : Tracer {
    override fun createSpan(
        name: String,
        initialAttributes: Attributes,
        spanKind: SpanKind,
        parentContext: Context?,
    ): TraceSpan = TraceSpan.None
}
