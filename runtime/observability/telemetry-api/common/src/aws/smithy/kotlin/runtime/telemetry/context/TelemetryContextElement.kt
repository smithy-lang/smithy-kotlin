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
}

/**
 * Extract the current telemetry [Context] from the coroutine context if available.
 */
@InternalApi
public val CoroutineContext.telemetryContext: Context?
    get() = get(TelemetryContextElement)?.context
