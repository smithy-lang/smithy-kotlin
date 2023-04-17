/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

// FIXME - tracer should take more than ID when creating a span (so we can set parent, attributes, etc)

/**
 * An object which can create new tracing spans and hand events off to tracing probe(s).
 */
public interface Tracer {
    /**
     * Create a "root" trace span, from which all child spans will be created for a given context.
     * @param name The name for the new root span.
     */
    public fun createRootSpan(name: String): TraceSpan
}

// FIXME - I think we can get rid of nested tracer stuff
private class NestedTracer(private val originSpan: TraceSpan, private val rootPrefix: String) : Tracer {
    override fun createRootSpan(name: String): TraceSpan {
        val fullId = if (rootPrefix.isBlank()) name else "$rootPrefix-$name"
        return originSpan.child(fullId)
    }
}

/**
 * Creates a new [Tracer] using this [TraceSpan] as a parent. This new tracer uses the same trace probe configuration as
 * the origin trace span. Child spans (including roots) created from the new tracer will be descendents of this trace
 * span.
 * @param rootPrefix A string to prepend to all root IDs for this tracer. This allows easily marking a tracer's spans as
 * being specific to a given service or use case. If this argument is blank, root IDs will be unprefixed. If this
 * argument is non-blank, the given prefix will be prepended to root IDs separated by a hyphen (`-`).
 */
public fun TraceSpan.asNestedTracer(rootPrefix: String): Tracer = NestedTracer(this, rootPrefix)
