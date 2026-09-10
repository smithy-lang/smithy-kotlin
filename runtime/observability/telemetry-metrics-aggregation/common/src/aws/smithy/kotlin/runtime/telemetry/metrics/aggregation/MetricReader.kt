/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.logging.Logger

/**
 * Decides *when* aggregated metrics are collected, and owns the [MetricExporter] they are handed to.
 *
 * Exporter-on-reader rather than exporter-on-provider: the collection interval and the temporality it
 * implies are properties of a reader, so pairing them with the exporter they govern is what makes two
 * destinations on two intervals expressible. The alternative — one exporter configured on the provider,
 * with the interval alongside it — cannot express that.
 *
 * **Sealed on purpose.** The extension point intended for callers and for future backends is
 * [MetricExporter], not this interface — an exporter says *where* metrics go and needs no access to
 * aggregation internals, while a reader is coupled to `SdkMeterProvider.collect` and to the provider's
 * lifecycle. Sealing it now keeps [install] free to change; unsealing later is a compatible change,
 * whereas retracting a third-party extension point is not.
 */
public sealed interface MetricReader {
    /**
     * Bind the provider this reader collects from and the logger it reports failures to, then begin
     * whatever scheduling the implementation performs.
     *
     * Called exactly once, by [SdkTelemetryProvider]'s initializer. Not part of the supported surface: the
     * provider is constructed after the reader — the caller supplies the reader — so there is a genuine
     * two-phase initialization here that callers should never drive themselves.
     */
    @InternalApi
    public fun install(provider: SdkMeterProvider, logger: Logger)

    /** Collect and export immediately. Safe to call concurrently and safe to call after [close]. */
    public suspend fun flush()

    /** Final flush, then release the exporter and any scheduling resources. Idempotent. */
    public suspend fun close()
}
