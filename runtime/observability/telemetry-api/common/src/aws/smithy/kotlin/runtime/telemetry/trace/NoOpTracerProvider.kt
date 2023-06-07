/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes

internal object NoOpTracerProvider : TracerProvider {
    override fun getTracer(scope: String, attributes: Attributes): Tracer = NoOpTracer
}

private object NoOpTracer : Tracer {
    override fun createSpan(
        name: String,
        parentContext: TraceContext?,
        initialAttributes: Attributes,
        spanKind: SpanKind,
    ): TraceSpan = NoOpTraceSpan
}

private object NoOpTraceSpan : TraceSpan {
    override val name: String = "NoOpSpan"
    override val traceContext: TraceContext = TraceContext.NONE
    override fun emitEvent(name: String, attributes: Attributes) {}
    override fun setStatus(status: SpanStatus) {}

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T) {}
    override fun setAttributes(attributes: Attributes) {}
    override fun end() { }
}
