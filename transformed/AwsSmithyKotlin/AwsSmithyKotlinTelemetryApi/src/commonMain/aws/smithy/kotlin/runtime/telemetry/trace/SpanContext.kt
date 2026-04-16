/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.trace

/**
 * The immutable state that must be serialized and propagated as part of a distributed trace context.
 */
public interface SpanContext {
    public companion object {
        public val Invalid: SpanContext = InvalidSpanContext
    }

    /**
     * The unique trace identifier this span belongs to
     */
    public val traceId: String

    /**
     * The unique span identifier
     */
    public val spanId: String

    /**
     * True if the [SpanContext] was propagated from a remote parent
     */
    public val isRemote: Boolean

    /**
     * True if the [SpanContext] has a non-zero [traceId] and [spanId]
     */
    public val isValid: Boolean
}

private object InvalidSpanContext : SpanContext {
    override val traceId: String = "00000000000000000000000000000000"
    override val spanId: String = "0000000000000000"
    override val isRemote: Boolean = false
    override val isValid: Boolean = false
}
