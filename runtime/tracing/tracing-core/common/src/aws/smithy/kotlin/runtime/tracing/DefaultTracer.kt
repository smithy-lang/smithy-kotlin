/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.Uuid
import aws.smithy.kotlin.runtime.util.mutableAttributes

private class DefaultTraceSpan(
    private val tracer: DefaultTracer,
    override val id: String,
    name: String,
    override val parent: TraceSpan?,
) : TraceSpan {

    // FIXME - do we want mutable attributes or set/get (which would allow delayed construction)?
    override val attributes: MutableAttributes by lazy { mutableAttributes() }

    override val metadata: TraceSpanMetadata = TraceSpanMetadata(
        parent?.metadata?.traceId ?: id,
        name,
    )

    init {
        tracer.probe.spanCreated(this)
    }

    override fun child(name: String): TraceSpan = DefaultTraceSpan(tracer, tracer.newSpanId(), name, this)

    override fun close(): Unit = tracer.probe.spanClosed(this)

    override fun postEvent(event: TraceEvent) = tracer.probe.postEvent(this, event)
}

// TODO - evaluate internal API or experimental for DefaultTracer (and potentially other trace APIs)?
/**
 * The default [Tracer] implementation. This tracer allows configuring one or more [TraceProbe]'s to
 * which events will be omitted.
 * @param probes [TraceProbe]'s to which events will be omitted.
 */
public class DefaultTracer(
    vararg probes: TraceProbe,
) : Tracer {

    internal val probe: TraceProbe = MultiTraceProbe(*probes)
    override fun createRootSpan(name: String): TraceSpan =
        DefaultTraceSpan(this, newSpanId(), name, null)

    @OptIn(Uuid.WeakRng::class)
    internal fun newSpanId(): String = Uuid.random().toString()
}
