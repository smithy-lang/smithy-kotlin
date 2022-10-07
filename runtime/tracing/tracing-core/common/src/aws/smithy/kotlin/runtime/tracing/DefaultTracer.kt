/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

private class DefaultTraceSpan(
    private val probe: TraceProbe,
    override val parent: TraceSpan?,
    override val id: String,
) : TraceSpan {
    override fun child(id: String): TraceSpan = DefaultTraceSpan(probe, this, id)

    override fun close(): Unit = probe.spanClosed(this)

    override fun postEvents(events: Iterable<TraceEvent>) {
        probe.postEvents(this, events)
    }
}

/**
 * The default [Tracer] implementation. This tracer allows configuring the [TraceProbe] to which events will be omitted.
 * @param probe The [TraceProbe] to which events will be omitted.
 * @param rootId The ID for the root [TraceSpan] of this tracer.
 */
public class DefaultTracer(private val probe: TraceProbe, private val rootId: String) : Tracer {
    override val rootSpan: TraceSpan by lazy { DefaultTraceSpan(probe, null, rootId) }
}
