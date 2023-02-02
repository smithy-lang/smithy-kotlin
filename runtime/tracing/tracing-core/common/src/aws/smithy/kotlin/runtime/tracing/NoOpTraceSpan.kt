/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.InternalApi

private data class NoOpTraceSpanImpl(override val id: String) : TraceSpan {
    override val parent: TraceSpan? = this

    override fun child(id: String): TraceSpan = this
    override fun close() { }
    override fun postEvent(event: TraceEvent) { }
}

/**
 * A [TraceSpan] that takes no actions. This object is provided mainly for unit tests of methods/dependencies which
 * require a [TraceSpan] but for which no verification of tracing need occur.
 */
@InternalApi
public val NoOpTraceSpan: TraceSpan = NoOpTraceSpanImpl("no-op")
