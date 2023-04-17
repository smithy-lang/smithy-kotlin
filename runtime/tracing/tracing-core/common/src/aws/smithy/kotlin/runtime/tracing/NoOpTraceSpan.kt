/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.mutableAttributes

private data class NoOpTraceSpanImpl(override val id: String) : TraceSpan {
    override val parent: TraceSpan = this

    override val attributes: MutableAttributes = mutableAttributes()
    override val metadata: TraceSpanMetadata = TraceSpanMetadata(id, id)
    override fun child(name: String): TraceSpan = this
    override fun close() { }
    override fun postEvent(event: TraceEvent) { }
}

/**
 * A [TraceSpan] that takes no actions. This object is provided mainly for unit tests of methods/dependencies which
 * require a [TraceSpan] but for which no verification of tracing need occur.
 */
@InternalApi
public val NoOpTraceSpan: TraceSpan = NoOpTraceSpanImpl("no-op")
