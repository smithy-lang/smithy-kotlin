/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.telemetry.GlobalTelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import io.opentelemetry.api.OpenTelemetry

/**
 * [TelemetryProvider] based on [OpenTelemetry](https://opentelemetry.io/).
 *
 * @param otel the OpenTelemetry API instance
 * @param loggerProvider the logger provider to use (defaults to the [GlobalTelemetryProvider] log provider)
 * A provider is taken explicitly because OpenTelemetry does not provide a logging API, only a log bridge for
 * existing logging implementations.
 */
@ExperimentalApi
public class OpenTelemetryProvider(
    private val otel: OpenTelemetry,
    override val loggerProvider: LoggerProvider = GlobalTelemetryProvider.instance.loggerProvider,
) : TelemetryProvider {
    override val tracerProvider: TracerProvider = OtelTracerProvider(otel)
    override val meterProvider: MeterProvider = OtelMeterProvider(otel)
    override val contextManager: ContextManager = OtelContextManager
}
