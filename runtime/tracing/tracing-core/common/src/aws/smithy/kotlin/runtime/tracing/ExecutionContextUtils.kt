/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.get

public object TracingContext {
    /**
     * The active [TraceSpan] that receives events.
     */
    public val TraceSpan: AttributeKey<TraceSpan> = AttributeKey("TraceSpan")
}

public val ExecutionContext.traceSpan: TraceSpan
    get() = get(TracingContext.TraceSpan)

public fun ExecutionContext.pushChildTraceSpan(id: String) {
    val existingSpan = checkNotNull(getOrNull(TracingContext.TraceSpan)) { "Missing an active trace span" }
    set(TracingContext.TraceSpan, existingSpan.child(id))
}

public fun ExecutionContext.popChildTraceSpan() {
    val existingSpan = checkNotNull(getOrNull(TracingContext.TraceSpan)) { "Missing an active trace span" }
    val parentSpan = checkNotNull(existingSpan.parent) { "The active trace span has no parent and cannot be popped" }
    set(TracingContext.TraceSpan, parentSpan)
}

public inline fun <R> ExecutionContext.childTraceSpan(id: String, block: () -> R): R {
    pushChildTraceSpan(id)
    try {
        return block()
    } finally {
        popChildTraceSpan()
    }
}
