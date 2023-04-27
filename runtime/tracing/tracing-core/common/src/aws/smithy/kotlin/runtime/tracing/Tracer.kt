/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

// FIXME - we probably want to be able to set attributes at span creation time (it can affect sampling decisions in OTeL
//         for instance)

/**
 * An object which can create new tracing spans
 */
public interface Tracer {
    /**
     * Create a new trace span for a given context. Instrumented code MUST call this from the same thread
     * that the span will be used on.
     *
     * @param name The name for the new root span.
     * @param parentContext The parent context to use for this span
     */
    public fun createSpan(name: String, parentContext: TraceContext? = null): TraceSpan
}
