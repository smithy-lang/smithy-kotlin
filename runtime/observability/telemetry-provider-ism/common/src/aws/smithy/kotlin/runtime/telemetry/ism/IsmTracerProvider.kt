/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.trace.*

public class IsmTracerProvider : TracerProvider {
    override fun getOrCreateTracer(scope: String): Tracer {
        TODO("Not yet implemented")
    }
}

public class IsmTracer : Tracer {
    override fun createSpan(
        name: String,
        initialAttributes: Attributes,
        spanKind: SpanKind,
        parentContext: Context?
    ): TraceSpan {
        TODO("Not yet implemented")
    }
}

public class IsmTraceSpan : TraceSpan {
    override val spanContext: SpanContext
        get() = TODO("Not yet implemented")

    override fun <T : Any> set(key: AttributeKey<T>, value: T) {
        TODO("Not yet implemented")
    }

    override fun mergeAttributes(attributes: Attributes) {
        TODO("Not yet implemented")
    }

    override fun emitEvent(name: String, attributes: Attributes) {
        TODO("Not yet implemented")
    }

    override fun setStatus(status: SpanStatus) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
