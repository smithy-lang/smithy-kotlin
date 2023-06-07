/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

/**
 * Contains the state that must propagate to child spans and across process/network boundaries
 * in a distributed trace.
 */
public interface TraceContext {
    public companion object {
        /**
         * A trace context that can be used for no-op implementations
         */
        public val NONE: TraceContext = object : TraceContext {
            override val traceId: String = "00000000000000000000000000000000"
            override val spanId: String = "0000000000000000"
            override val parentId: String? = null
        }
    }

    /**
     * The unique trace ID this span belongs to
     */
    public val traceId: String

    /**
     * The unique ID of the span
     */
    public val spanId: String

    /**
     * The ID of the parent this span belongs to. May be null for a "root" span.
     */
    public val parentId: String?
}
