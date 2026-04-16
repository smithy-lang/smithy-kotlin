/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineContext] element that carries a telemetry [Context].
 * @param context The active context
 */
@InternalApi
public expect class TelemetryContextElement(context: Context) : CoroutineContext.Element {
    public companion object Key : CoroutineContext.Key<TelemetryContextElement>

    public val context: Context
    override val key: CoroutineContext.Key<*>
    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E?
    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R
    override fun plus(context: CoroutineContext): CoroutineContext
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext
}

/**
 * Extract the current telemetry [Context] from the coroutine context if available.
 */
@InternalApi
public val CoroutineContext.telemetryContext: Context?
    get() = get(TelemetryContextElement)?.context
