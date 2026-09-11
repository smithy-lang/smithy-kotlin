/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.internal

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.io.SdkManagedCloseable
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider

/**
 * Reference-counted wrapper around a closeable [TelemetryProvider]. [SdkManagedCloseable] supplies the
 * share count and closes the delegate when it drops to zero; `by delegate` forwards the telemetry
 * members so the wrapper is indistinguishable from what it wraps.
 */
private class ManagedTelemetryProvider<T>(delegate: T) :
    SdkManagedCloseable(delegate),
    TelemetryProvider by delegate
    where T : TelemetryProvider, T : Closeable

/**
 * Wrap this provider for reference counting if it holds resources, otherwise return it unchanged.
 *
 * **Call this only where the SDK itself constructs the provider**, as
 * [aws.smithy.kotlin.runtime.http.engine.internal.manage] is applied to SDK-constructed HTTP engines and
 * not to caller-supplied ones. The share count starts at zero, so one client's `share()`/`unshare()` pair
 * closes the delegate - wrapping a caller-supplied provider would shut down their telemetry when the first
 * client closed.
 *
 * Already-wrapped providers are returned as is, since a second count would only close the delegate once
 * both reached zero. Providers that are not [Closeable] (`None`, OTel, EMF) hold nothing to release.
 */
@InternalApi
public fun TelemetryProvider.manage(): TelemetryProvider = when (this) {
    is ManagedTelemetryProvider<*> -> this
    is Closeable -> ManagedTelemetryProvider(this)
    else -> this
}
