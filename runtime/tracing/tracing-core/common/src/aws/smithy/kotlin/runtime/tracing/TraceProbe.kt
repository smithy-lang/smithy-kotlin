/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

/**
 * A sink that receives events.
 */
public interface TraceProbe {
    /**
     * Called when events have been posted to a [TraceSpan].
     * @param span The span in which the events occurred.
     * @param events The events which occurred.
     */
    public fun postEvents(span: TraceSpan, events: Iterable<TraceEvent>)

    /**
     * Called when a [TraceSpan] has been closed and no further events will be posted to it.
     * @param span The span which has been closed.
     */
    public fun spanClosed(span: TraceSpan)
}
