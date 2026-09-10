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
 * Reference-counted wrapper around a closeable [TelemetryProvider].
 *
 * Two things are combined here, and both are necessary:
 *
 * - `SdkManagedCloseable(closeable)` supplies the share count and closes the provider when it drops to zero.
 * - `TelemetryProvider by delegate` forwards all four telemetry properties, so the wrapper is
 *   indistinguishable from the provider it wraps at every use site.
 *
 * Delegation rather than a hand-written forwarding class: the four properties are read-only and forwarded
 * unchanged, and `by` cannot fall out of date if the interface gains a member.
 *
 * @param delegate the provider whose telemetry members are forwarded.
 * @param closeable the same instance as [delegate], viewed as [Closeable]. Two parameters rather than one
 *   type-parameterized constructor: `TelemetryProvider` has no closeable sub-interface to constrain against
 *   (unlike `CloseableHttpClientEngine`), and inferring a type argument from a smart cast to an intersection
 *   type is not something to rely on. Both parameters are always the same object — see [manage].
 */
private class ManagedTelemetryProvider(
    delegate: TelemetryProvider,
    closeable: Closeable,
) : SdkManagedCloseable(closeable),
    TelemetryProvider by delegate

/**
 * Wrap this provider for reference counting if it holds resources, otherwise return it unchanged.
 *
 * **Call this only where the SDK itself constructs the provider**, mirroring how
 * [aws.smithy.kotlin.runtime.http.engine.internal.manage] is applied to SDK-constructed HTTP engines and not
 * to caller-supplied ones. The share count starts at zero, so a single client's `share()`/`unshare()` pair
 * ends at zero and closes the delegate. Wrapping a provider the caller built and assigned would therefore
 * shut down their telemetry when the first client closed — the resource stays theirs precisely because it is
 * left unwrapped, and a generated client's `addIfManaged(config.telemetryProvider)` skips anything that is
 * not [aws.smithy.kotlin.runtime.io.SdkManaged].
 *
 * Three cases, in order:
 *
 * 1. Already wrapped — returned as is. Wrapping twice would create a second, independent count, and the
 *    inner provider would then close only when *both* reached zero.
 * 2. [Closeable] — wrapped. This is the
 *    [aws.smithy.kotlin.runtime.telemetry.metrics.aggregation.SdkTelemetryProvider] case: something must
 *    eventually stop the reader.
 * 3. Anything else — returned unchanged. Most providers (`None`, OTel, EMF) hold nothing that needs
 *    releasing, and wrapping them would add a count that never does anything.
 */
@InternalApi
public fun TelemetryProvider.manage(): TelemetryProvider = when (this) {
    is ManagedTelemetryProvider -> this
    is Closeable -> ManagedTelemetryProvider(this, this)
    else -> this
}
