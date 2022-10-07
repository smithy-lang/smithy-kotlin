/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

/**
 * An object which can create new tracing spans and hand events off to tracing probe(s).
 */
public interface Tracer {
    /**
     * Get the "root" trace span, from which all child spans will be created for a given context.
     */
    public val rootSpan: TraceSpan
}

private class NestedTracer(private val originSpan: TraceSpan, private val rootId: String) : Tracer {
    override val rootSpan: TraceSpan by lazy { originSpan.child(rootId) }
}

/**
 * Creates a new [Tracer] using this [TraceSpan] as a parent. This new tracer uses the same trace probe configuration as
 * the origin trace span. Child spans (including the root) created from this new tracer will be descendents of this
 * trace span.
 * @param rootId The ID for the root [TraceSpan] of the new tracer.
 */
public fun TraceSpan.asNestedTracer(rootId: String): Tracer = NestedTracer(this, rootId)
