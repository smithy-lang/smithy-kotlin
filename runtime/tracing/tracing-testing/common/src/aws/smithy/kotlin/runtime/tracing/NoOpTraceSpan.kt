/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

/**
 * A [TraceSpan] that takes no actions. This object is provided mainly for unit tests of methods/dependencies which
 * require a [TraceSpan] but for which no verification of tracing need occur.
 */
public object NoOpTraceSpan : TraceSpan {
    override val id: String = ""
    override val parent: TraceSpan? = null

    override fun child(id: String): TraceSpan = this
    override fun close() { }
    override fun postEvents(events: Iterable<TraceEvent>) { }
}
