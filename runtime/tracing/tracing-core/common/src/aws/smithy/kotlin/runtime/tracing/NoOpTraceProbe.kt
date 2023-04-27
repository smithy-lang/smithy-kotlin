/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

/**
 * A [TraceProbe] which takes no action. No events are logged or handled in any way.
 */
public object NoOpTraceProbe : TraceProbe {
    override fun postEvent(span: TraceSpanData, event: TraceEvent) { }
    override fun spanClosed(span: TraceSpan) { }
    override fun spanCreated(span: TraceSpan) { }
}
