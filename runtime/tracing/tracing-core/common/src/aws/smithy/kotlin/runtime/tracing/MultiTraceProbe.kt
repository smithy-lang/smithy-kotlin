/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

/**
 * [TraceProbe] that fans out to multiple probes.
 */
internal class MultiTraceProbe(
    private vararg val probes: TraceProbe,
) : TraceProbe {
    override fun spanCreated(span: TraceSpan) {
        probes.forEach { probe -> probe.spanCreated(span) }
    }

    override fun postEvent(span: TraceSpanData, event: TraceEvent) {
        probes.forEach { probe -> probe.postEvent(span, event) }
    }

    override fun spanClosed(span: TraceSpan) {
        probes.forEach { probe -> probe.spanClosed(span) }
    }
}
