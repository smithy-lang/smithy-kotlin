/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.InternalApi

private data class NoOpTraceSpanImpl(override val id: String, override val parent: TraceSpan?) : TraceSpan {
    override fun child(id: String): TraceSpan = NoOpTraceSpanImpl(id, this)
    override fun close() { }
    override fun postEvents(events: Iterable<TraceEvent>) { }
}

/**
 * A [TraceSpan] that takes no actions. This object is provided mainly for unit tests of methods/dependencies which
 * require a [TraceSpan] but for which no verification of tracing need occur.
 */
@InternalApi
public val NoOpTraceSpan: TraceSpan = NoOpTraceSpanImpl("", null)
