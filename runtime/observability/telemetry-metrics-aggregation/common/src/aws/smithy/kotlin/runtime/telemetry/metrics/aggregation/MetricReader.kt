/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.logging.Logger

/**
 * Decides *when* aggregated metrics are collected, and owns the [MetricExporter] they are handed to.
 * Pairing the two is what makes two destinations on two intervals expressible.
 *
 * Sealed because the intended extension point is [MetricExporter]: an exporter says only *where* metrics
 * go, while a reader is coupled to `AggregatingMeterProvider.collect` and the provider's lifecycle. Unsealing
 * later is a compatible change; retracting an extension point is not.
 */
public sealed interface MetricReader {
    /**
     * Bind the provider this reader collects from and the logger it reports failures to, then begin
     * scheduling.
     *
     * Called exactly once, by [AggregatingTelemetryProvider]'s initializer. Internal because the provider is
     * constructed after the reader the caller supplied, so this two-phase initialization is not something
     * callers should drive.
     */
    @InternalApi
    public fun install(provider: AggregatingMeterProvider, logger: Logger)

    /** Collect and export immediately. Safe to call concurrently and safe to call after [close]. */
    public suspend fun flush()

    /** Final flush, then release the exporter and any scheduling resources. Idempotent. */
    public suspend fun close()
}
