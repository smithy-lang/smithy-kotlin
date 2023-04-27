/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.*

private class DefaultTraceSpan(
    private val tracer: DefaultTracer,
    override val context: TraceContext,
    override val name: String,
) : TraceSpan, TraceSpanData {

    override var spanStatus: TraceSpanStatus = TraceSpanStatus.UNSET

    private val _attrField = lazy { mutableAttributes() }
    private val _attributes: MutableAttributes
        get() = _attrField.value

    override val attributes: Attributes
        get() = if (_attrField.isInitialized()) _attributes else emptyAttributes()

    init {
        tracer.probe.spanCreated(this)
    }

    override fun child(name: String): TraceSpan = tracer.createSpan(name, context)

    override fun close(): Unit = tracer.probe.spanClosed(this)

    override fun postEvent(event: TraceEvent) = tracer.probe.postEvent(this, event)
    override fun <T : Any> setAttr(key: String, value: T) {
        _attributes.set(key, value)
    }
}

/**
 * The default [Tracer] implementation. This tracer allows configuring one or more [TraceProbe]'s to
 * which events will be omitted.
 * @param probes [TraceProbe]'s to which events will be omitted.
 */
public class DefaultTracer(
    vararg probes: TraceProbe,
) : Tracer {

    internal val probe: TraceProbe = MultiTraceProbe(*probes)

    override fun createSpan(name: String, parentContext: TraceContext?): TraceSpan {
        val traceId = parentContext?.traceId ?: newId()
        val parentSpanId = parentContext?.spanId
        val spanId = newId()
        val ctx = DefaultTraceContext(traceId, spanId, parentSpanId)
        return DefaultTraceSpan(this, ctx, name)
    }

    @OptIn(Uuid.WeakRng::class)
    private fun newId(): String = Uuid.random().toString()
}

internal data class DefaultTraceContext(
    override val traceId: String,
    override val spanId: String,
    override val parentId: String? = null,
) : TraceContext
