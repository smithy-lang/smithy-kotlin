/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.context.telemetryContext
import aws.smithy.kotlin.runtime.telemetry.telemetryProvider
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * A [CoroutineContext] element that carries a [TraceSpan].
 * @param traceSpan The active trace span for this context.
 */
@InternalApi
public data class TraceSpanContext(public val traceSpan: TraceSpan) : AbstractCoroutineContextElement(TraceSpanContext) {
    public companion object Key : CoroutineContext.Key<TraceSpanContext>
    override fun toString(): String = "TraceSpanContextElement($traceSpan)"
}

/**
 * Gets the active [TraceSpan] from this [CoroutineContext].
 */
@InternalApi
public val CoroutineContext.traceSpan: TraceSpan?
    get() = get(TraceSpanContext)?.traceSpan

/**
 * Creates a new [TraceSpan] and executes [block] within the scope of the new span.
 * The [block] of code is executed with a new coroutine context that contains the newly
 * created span set.
 */
@InternalApi
public suspend inline fun <R> Tracer.withSpan(
    name: String,
    initialAttributes: Attributes = emptyAttributes(),
    spanKind: SpanKind = SpanKind.INTERNAL,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.(span: TraceSpan) -> R,
): R {
    val parent = coroutineContext.telemetryContext
    val span = createSpan(name, initialAttributes, spanKind, parent)
    return withSpan(span, context, block)
}

/**
 * Executes [block] within the scope of [TraceSpan]. The [block] of code is executed
 * with a new coroutine context that contains the [span] set in the context.
 */
@InternalApi
public suspend inline fun <R> withSpan(
    span: TraceSpan,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.(span: TraceSpan) -> R,
): R = try {
    withContext(context + TraceSpanContext(span)) {
        block(span)
    }
} catch (ex: Exception) {
    if (ex !is CancellationException) {
        span.setStatus(SpanStatus.ERROR)
    }
    span.recordException(ex, true)
    throw ex
} finally {
    span.close()
}

/**
 * Creates a new [TraceSpan] and executes [block] within the scope of the new span.
 * The [block] of code is executed with a new coroutine context that contains the newly
 * created span set.
 */
@InternalApi
public suspend inline fun <reified T, R> withSpan(
    name: String,
    initialAttributes: Attributes = emptyAttributes(),
    spanKind: SpanKind = SpanKind.INTERNAL,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.(span: TraceSpan) -> R,
): R {
    val sourceComponent = requireNotNull(T::class.qualifiedName) { "withSpan<T> cannot be used on an anonymous object" }
    return withSpan(sourceComponent, name, initialAttributes, spanKind, context, block)
}

/**
 * Creates a new [TraceSpan] using [Tracer] for [sourceComponent] and executes [block]
 * within the scope of the new span. The [block] of code is executed with a new coroutine
 * context that contains the newly created span set.
 */
@InternalApi
public suspend inline fun <R> withSpan(
    sourceComponent: String,
    name: String,
    initialAttributes: Attributes = emptyAttributes(),
    spanKind: SpanKind = SpanKind.INTERNAL,
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.(span: TraceSpan) -> R,
): R {
    val tracer = coroutineContext.telemetryProvider.tracerProvider.getOrCreateTracer(sourceComponent)
    return tracer.withSpan(name, initialAttributes, spanKind, context, block)
}
