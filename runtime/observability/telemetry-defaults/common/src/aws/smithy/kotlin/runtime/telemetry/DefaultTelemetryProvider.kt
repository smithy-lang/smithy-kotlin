/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry

import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.DefaultLoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider

/**
 * A telemetry provider that uses the default logger for a platform if one exists (e.g. SLF4J on JVM) and
 * is a no-op for other telemetry signals.
 */
public object DefaultTelemetryProvider : TelemetryProvider {
    override val loggerProvider: LoggerProvider = DefaultLoggerProvider
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None
    override val meterProvider: MeterProvider = MeterProvider.None
}
