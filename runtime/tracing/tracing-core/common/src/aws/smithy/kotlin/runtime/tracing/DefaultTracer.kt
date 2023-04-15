/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.mutableAttributes

private class DefaultTraceSpan(
    private val probe: TraceProbe,
    override val parent: TraceSpan?,
    override val id: String,
) : TraceSpan {
    override val attributes: MutableAttributes by lazy { mutableAttributes() }

    override var spanStatus: TraceSpanStatus = TraceSpanStatus.UNSET
    init {
        probe.spanCreated(this)
    }

    override fun child(id: String): TraceSpan = DefaultTraceSpan(probe, this, id)

    override fun close(): Unit = probe.spanClosed(this)

    override fun postEvent(event: TraceEvent) = probe.postEvent(this, event)
}

/**
 * The default [Tracer] implementation. This tracer allows configuring the [TraceProbe] to which events will be omitted.
 * @param probe The [TraceProbe] to which events will be omitted.
 * @param rootPrefix A string to prepend to all root IDs for this tracer. This allows easily marking a tracer's spans as
 * being specific to a given service or use case. If this argument is blank, root IDs will be unprefixed. If this
 * argument is non-blank, the given prefix will be prepended to root IDs separated by a hyphen (`-`).
 */
public class DefaultTracer(private val probe: TraceProbe, private val rootPrefix: String) : Tracer {
    override fun createRootSpan(id: String): TraceSpan {
        val fullId = if (rootPrefix.isBlank()) id else "$rootPrefix-$id"
        return DefaultTraceSpan(probe, null, fullId)
    }
}
