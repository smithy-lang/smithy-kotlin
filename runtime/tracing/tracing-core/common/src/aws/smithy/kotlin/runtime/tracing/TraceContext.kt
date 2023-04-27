/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

public interface TraceContext {
    /**
     * The unique ID of the trace this span belongs to
     */
    public val traceId: String

    /**
     * The identifier for this span, which should be unique among sibling spans within the same parent. Trace span IDs
     * may be used by probes to collate or decorate event output.
     */
    public val spanId: String

    /**
     * The parent ID of this span (if one exists)
     */
    public val parentId: String?
}
