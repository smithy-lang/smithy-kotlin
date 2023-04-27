/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing.otel

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.time.toJvmInstant
import aws.smithy.kotlin.runtime.tracing.*
import aws.smithy.kotlin.runtime.tracing.TraceSpan
import aws.smithy.kotlin.runtime.tracing.Tracer
import aws.smithy.kotlin.runtime.util.*
import io.opentelemetry.api.logs.GlobalLoggerProvider
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.api.common.AttributeKey as OtelAttributeKey
import io.opentelemetry.api.common.Attributes as OtelAttributes
import io.opentelemetry.api.trace.Span as OpenTelemetrySpan
import io.opentelemetry.api.trace.Tracer as OpenTelemetryTracer

@OptIn(ExperimentalApi::class)
private class OtelSpanAdapter(
    private val tracer: OtelTracerAdapter,
    override val name: String,
    private val otelSpan: OpenTelemetrySpan,
    override val context: OtelTraceContext,
) : TraceSpan {
    override var spanStatus: TraceSpanStatus = TraceSpanStatus.UNSET
        set(value) {
            check(value != TraceSpanStatus.UNSET) { "UNSET is not a valid span status value to set explicitly" }
            otelSpan.setStatus(value.otelStatusCode)
            field = value
        }

    override fun child(name: String): TraceSpan = tracer.createSpan(name, context)

    override fun postEvent(event: TraceEvent) {
        if (context.eventFilter?.shouldProcess(event) == false) return

        when (val data = event.data) {
            is TraceEventData.Log -> {
                val logger = context.loggerProvider.get(data.sourceComponent)
                logger.logRecordBuilder().apply {
                    setBody(data.content()?.toString())
                    setEpoch(event.timestamp.toJvmInstant())
                    setSeverity(event.level.otelSeverity)
                    setContext(context.otelContext)
                    setAllAttributes(event.attributes.toOtelAttributes())
                }.emit()
            }
            null -> otelSpan.addEvent(event.name, event.attributes.toOtelAttributes(), event.timestamp.toJvmInstant())
            // TODO - process metric events
            else -> return
        }
    }

    override fun <T : Any> setAttr(key: String, value: T) {
        val attributes = attributesOf { key to value }
        otelSpan.setAllAttributes(attributes.toOtelAttributes())
    }

    override fun close() {
        otelSpan.end()
    }
}

@Suppress("UNCHECKED_CAST")
private fun Attributes.toOtelAttributes(): OtelAttributes {
    val keys = this.keys
    if (keys.isEmpty()) return OtelAttributes.empty()
    val attrs = OtelAttributes.builder()
    keys.forEach {
        val key = it as AttributeKey<Any>
        val value = get(key)
        when (value) {
            is String -> attrs.put(key.name, value)
            is Long -> attrs.put(key.name, value)
            is Boolean -> attrs.put(key.name, value)
            is Double -> attrs.put(key.name, value)
            is List<*> -> {
                when (value.firstOrNull()) {
                    is String -> attrs.put(OtelAttributeKey.stringArrayKey(key.name), value as List<String>)
                    is Long -> attrs.put(OtelAttributeKey.longArrayKey(key.name), value as List<Long>)
                    is Boolean -> attrs.put(OtelAttributeKey.booleanArrayKey(key.name), value as List<Boolean>)
                    is Double -> attrs.put(OtelAttributeKey.doubleArrayKey(key.name), value as List<Double>)
                }
            }
        }
    }

    return attrs.build()
}

private val TraceSpanStatus.otelStatusCode: StatusCode
    get() = when (this) {
        TraceSpanStatus.UNSET -> StatusCode.UNSET
        TraceSpanStatus.ERROR -> StatusCode.ERROR
        TraceSpanStatus.OK -> StatusCode.OK
    }

private val EventLevel.otelSeverity: Severity
    get() = when (this) {
        EventLevel.Fatal -> Severity.FATAL
        EventLevel.Error -> Severity.ERROR
        EventLevel.Warning -> Severity.WARN
        EventLevel.Info -> Severity.INFO
        EventLevel.Debug -> Severity.DEBUG
        EventLevel.Trace -> Severity.TRACE
    }

@OptIn(ExperimentalApi::class)
private data class OtelTraceContext(
    private val otelSpanContext: SpanContext,
    override val parentId: String?,
    val loggerProvider: LoggerProvider,
    val eventFilter: EventFilter?,
    val otelContext: Context,
) : TraceContext {
    override val traceId: String
        get() = otelSpanContext.traceId
    override val spanId: String
        get() = otelSpanContext.spanId
}

@OptIn(ExperimentalApi::class)
private class OtelTracerAdapter(
    private val tracer: OpenTelemetryTracer,
    private val eventFilter: EventFilter?,
) : Tracer {
    private val loggerProvider = GlobalLoggerProvider.get()
    override fun createSpan(name: String, parentContext: TraceContext?): TraceSpan {
        val currentContex = Context.current()
        val parentCtx = (parentContext as? OtelTraceContext)?.otelContext ?: currentContex
        val otelSpan = tracer.spanBuilder("name")
            .setParent(parentCtx)
            .startSpan()

        val otelContext = OtelTraceContext(
            otelSpan.spanContext,
            parentContext?.spanId,
            loggerProvider,
            eventFilter,
            currentContex,
        )

        return OtelSpanAdapter(this, name, otelSpan, otelContext)
    }
}

/**
 * A filter for SDK events that should be processed by OpenTelemetry
 */
@ExperimentalApi
public fun interface EventFilter {
    public fun shouldProcess(event: TraceEvent): Boolean
}

/**
 * Wrap an OpenTelemetry tracer as a [Tracer] instance. This will use the OTeL tracer to
 * create spans, process logs, etc.
 */
@ExperimentalApi
public fun OpenTelemetryTracer.asSdkTracer(
    filter: EventFilter? = null,
    // TODO - option for how to handle logs?
): Tracer = OtelTracerAdapter(this, filter)
