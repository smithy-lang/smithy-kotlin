/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An abstract implementation of a trace span. By default, this class uses no-op implementations for all members unless
 * overridden in a subclass.
 */
public abstract class AbstractTraceSpan : TraceSpan {
    override val spanContext: SpanContext = SpanContext.Invalid
    override fun emitEvent(name: String, attributes: Attributes) { }
    override fun setStatus(status: SpanStatus) { }
    override operator fun <T : Any> set(key: AttributeKey<T>, value: T) { }
    override fun mergeAttributes(attributes: Attributes) { }
    override fun close() { }
    override fun asContextElement(): CoroutineContext = EmptyCoroutineContext
}
