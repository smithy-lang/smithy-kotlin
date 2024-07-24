/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.micrometer

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.telemetry.GlobalTelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics

/**
 * [TelemetryProvider] based on [Micrometer](https://micrometer.io/).
 *
 * @param meterRegistry the Micrometer API instance (defaults to use [Metrics.globalRegistry])
 * @param loggerProvider the logger provider to use (defaults to the [GlobalTelemetryProvider] log provider)
 * A provider is taken explicitly because Micrometer does not provide a logging API, only a log bridge for
 * existing logging implementations.
 */
@ExperimentalApi
public class MicrometerTelemetryProvider(
    meterRegistry: MeterRegistry = Metrics.globalRegistry,
    override val loggerProvider: LoggerProvider = GlobalTelemetryProvider.instance.loggerProvider,
) : TelemetryProvider {
    override val meterProvider: MeterProvider = MicrometerMeterProvider(meterRegistry)
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None
}