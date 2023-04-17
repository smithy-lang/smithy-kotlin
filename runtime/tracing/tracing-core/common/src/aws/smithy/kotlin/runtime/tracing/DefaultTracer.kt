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

/**
 * The default [Tracer] implementation. This tracer allows configuring the [TraceProbe] to which events will be omitted.
 * @param probe The [TraceProbe] to which events will be omitted.
 * @param rootPrefix A string to prepend to all root IDs for this tracer. This allows easily marking a tracer's spans as
 * being specific to a given service or use case. If this argument is blank, root IDs will be unprefixed. If this
 * argument is non-blank, the given prefix will be prepended to root IDs separated by a hyphen (`-`).
 */
public class DefaultTracer(
    internal val probe: TraceProbe,
    private val rootPrefix: String,
) : Tracer {
    override fun createRootSpan(name: String): TraceSpan {
        // FIXME - do we need rootPrefix? Should we make it an attribute instead?
        // val fullId = if (rootPrefix.isBlank()) name else "$rootPrefix-$name"
        return DefaultTraceSpan(this, newSpanId(), name, null)
    }

    @OptIn(Uuid.WeakRng::class)
    internal fun newSpanId(): String = Uuid.random().toString()
}
