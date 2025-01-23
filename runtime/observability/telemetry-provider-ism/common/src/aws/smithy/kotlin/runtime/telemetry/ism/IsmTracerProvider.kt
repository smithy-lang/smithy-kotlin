/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.merge
import aws.smithy.kotlin.runtime.collections.toMutableAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.trace.*

internal class IsmTracerProvider(val spanListener: SpanListener) : TracerProvider {
    override fun getOrCreateTracer(scope: String): Tracer = IsmTracer(spanListener, scope)
}

private class IsmTracer(val spanListener: SpanListener, val scope: String) : Tracer {
    override fun createSpan(
        name: String,
        initialAttributes: Attributes,
        spanKind: SpanKind,
        parentContext: Context?
    ): TraceSpan = IsmTraceSpan(spanListener, scope, name, initialAttributes, spanKind, parentContext)
}

private class IsmTraceSpan(
    private val spanListener: SpanListener,
    private val scope: String,
    private val name: String,
    initialAttributes: Attributes,
    private val spanKind: SpanKind,
    parentContext: Context?
) : TraceSpan {
    private val attributes = initialAttributes.toMutableAttributes()
    private val selfContext: Context = spanListener.onNewSpan(parentContext, name, initialAttributes)

    override val spanContext: SpanContext = (selfContext as? HierarchicalContext)?.spanContext ?: SpanContext.Invalid

    override fun <T : Any> set(key: AttributeKey<T>, value: T) {
        attributes[key] = value
    }

    override fun mergeAttributes(attributes: Attributes) {
        this.attributes.merge(attributes)
    }

    override fun emitEvent(name: String, attributes: Attributes) = TODO("Not yet implemented")

    override fun setStatus(status: SpanStatus) = TODO("Not yet implemented")

    override fun close() = spanListener.onCloseSpan(selfContext)
}
