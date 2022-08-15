/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

public interface TraceProbe {
    public fun postEvents(span: TraceSpan, events: Iterable<TraceEvent>)
    public fun spanClosed(span: TraceSpan)
}
