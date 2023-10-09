/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes

internal object NoOpTracerProvider : TracerProvider {
    override fun getOrCreateTracer(scope: String): Tracer = NoOpTracer
}

private object NoOpTracer : Tracer {
    override fun createSpan(
        name: String,
        initialAttributes: Attributes,
        spanKind: SpanKind,
        parentContext: Context?,
    ): TraceSpan = NoOpTraceSpan
}

private object NoOpTraceSpan : TraceSpan {
    override val spanContext: SpanContext = SpanContext.Invalid
    override fun emitEvent(name: String, attributes: Attributes) {}
    override fun setStatus(status: SpanStatus) {}
    override operator fun <T : Any> set(key: AttributeKey<T>, value: T) {}
    override fun mergeAttributes(attributes: Attributes) {}
    override fun close() {}
}
