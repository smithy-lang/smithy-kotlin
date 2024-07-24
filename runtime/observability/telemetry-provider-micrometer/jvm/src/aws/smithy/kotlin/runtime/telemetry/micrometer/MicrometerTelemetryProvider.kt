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

@ExperimentalApi
public class MicrometerTelemetryProvider(
    meterRegistry: MeterRegistry = Metrics.globalRegistry,
    override val loggerProvider: LoggerProvider = GlobalTelemetryProvider.instance.loggerProvider,
) : TelemetryProvider {
    override val meterProvider: MeterProvider = MicrometerMeterProvider(meterRegistry)
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None
}