/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine scoped telemetry context used for carrying telemetry provider configuration
 * @param provider The telemetry provider to instrument with
 */
@InternalApi
public data class TelemetryProviderContext(
    val provider: TelemetryProvider,
) : AbstractCoroutineContextElement(TelemetryProviderContext) {
    public companion object Key : CoroutineContext.Key<TelemetryProviderContext>
    override fun toString(): String = "TelemetryContext($provider)"
}

/**
 * Get the active [TelemetryProvider] from this [CoroutineContext]. If none exists
 * a no-op provider will be returned.
 */
@InternalApi
public val CoroutineContext.telemetryProvider: TelemetryProvider
    get() = get(TelemetryProviderContext)?.provider ?: TelemetryProvider.None
