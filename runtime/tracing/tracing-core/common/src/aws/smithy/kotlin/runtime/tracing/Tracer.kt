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
     * Create a "root" trace span, from which all child spans will be created for a given context.
     */
    public fun createRootSpan(): TraceSpan
}
