/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.InternalApi

@InternalApi
public class DefaultTraceSpan(
    private val probe: TraceProbe,
    override val parent: TraceSpan?,
    override val id: String,
) : TraceSpan {
    override fun child(id: String): TraceSpan = DefaultTraceSpan(probe, parent, id)

    override fun close(): Unit = probe.spanClosed(this)

    override fun postEvents(events: Iterable<TraceEvent>) {
        probe.postEvents(this, events)
    }
}

@InternalApi
public class DefaultTracer(private val probe: TraceProbe, private val rootId: String) : Tracer {
    override fun createRootSpan(): TraceSpan = DefaultTraceSpan(probe, null, rootId)
}
