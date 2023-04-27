/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A [TraceSpan] that takes no actions. This object is provided mainly for unit tests of methods/dependencies which
 * require a [TraceSpan] but for which no verification of tracing need occur.
 */
@InternalApi
public object NoOpTraceSpan : TraceSpan {
    override val name: String = "no-op"
    override val context: TraceContext = DefaultTraceContext("no-op", "no-op")

    @Suppress("UNUSED_PARAMETER")
    override var spanStatus: TraceSpanStatus = TraceSpanStatus.UNSET
        set(value) {}
    override fun child(name: String): TraceSpan = this
    override fun postEvent(event: TraceEvent) { }

    override fun <T : Any> setAttr(key: String, value: T) { }
    override fun close() { }
}
