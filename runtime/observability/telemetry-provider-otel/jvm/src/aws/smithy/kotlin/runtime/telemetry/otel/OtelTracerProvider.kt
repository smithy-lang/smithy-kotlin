/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.trace.*
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span as OtelSpan
import io.opentelemetry.api.trace.SpanContext as OtelSpanContext
import io.opentelemetry.api.trace.SpanKind as OtelSpanKind
import io.opentelemetry.api.trace.StatusCode as OtelStatus
import io.opentelemetry.api.trace.Tracer as OtelTracer

internal class OtelTracerProvider(private val otel: OpenTelemetry) : TracerProvider {
    override fun getOrCreateTracer(scope: String): Tracer =
        OtelTracerImpl(otel.getTracer(scope))
}

private class OtelTracerImpl(
    private val otelTracer: OtelTracer,
) : Tracer {
    override fun createSpan(
        name: String,
        initialAttributes: Attributes,
        spanKind: SpanKind,
        parentContext: Context?,
    ): TraceSpan {
        val otelContext = (parentContext as? OtelContext)?.context

        val spanBuilder = otelTracer.spanBuilder(name)
            .apply {
                if (otelContext != null) {
                    setParent(otelContext)
                }

                if (!initialAttributes.isEmpty) {
                    setAllAttributes(initialAttributes.toOtelAttributes())
                }
            }
            .setSpanKind(spanKind.toOtelSpanKind())

        return OtelTraceSpanImpl(spanBuilder.startSpan())
    }
}

private class OtelSpanContextImpl(private val otelSpanContext: OtelSpanContext) : SpanContext {
    override val traceId: String
        get() = otelSpanContext.traceId
    override val spanId: String
        get() = otelSpanContext.spanId
    override val isRemote: Boolean
        get() = otelSpanContext.isRemote
    override val isValid: Boolean
        get() = otelSpanContext.isValid
}

internal class OtelTraceSpanImpl(
    private val otelSpan: OtelSpan,
) : TraceSpan {

    private val spanScope = otelSpan.makeCurrent()

    override val spanContext: SpanContext
        get() = OtelSpanContextImpl(otelSpan.spanContext)
    override fun <T : Any> set(key: AttributeKey<T>, value: T) {
        key.otelAttrKeyOrNull(value)?.let { otelKey ->
            otelSpan.setAttribute(otelKey, value)
        }
    }

    override fun mergeAttributes(attributes: Attributes) {
        otelSpan.setAllAttributes(attributes.toOtelAttributes())
    }

    override fun emitEvent(name: String, attributes: Attributes) {
        when (attributes.isEmpty) {
            true -> otelSpan.addEvent(name)
            false -> otelSpan.addEvent(name, attributes.toOtelAttributes())
        }
    }

    override fun setStatus(status: SpanStatus) {
        otelSpan.setStatus(status.toOtelStatus())
    }

    override fun close() {
        otelSpan.end()
        spanScope.close()
    }
}

private fun SpanStatus.toOtelStatus(): OtelStatus = when (this) {
    SpanStatus.UNSET -> OtelStatus.UNSET
    SpanStatus.OK -> OtelStatus.OK
    SpanStatus.ERROR -> OtelStatus.ERROR
}

private fun SpanKind.toOtelSpanKind(): OtelSpanKind = when (this) {
    SpanKind.INTERNAL -> OtelSpanKind.INTERNAL
    SpanKind.SERVER -> OtelSpanKind.SERVER
    SpanKind.CLIENT -> OtelSpanKind.CLIENT
}
