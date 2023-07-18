/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider

/**
 * Container for telemetry providers
 */
public interface TelemetryProvider {
    public companion object {
        /**
         * Default (no-op) telemetry provider
         */
        public val None: TelemetryProvider = NoOpTelemetryProvider
    }

    /**
     * Get the [TracerProvider] used to create new [aws.smithy.kotlin.runtime.telemetry.trace.Tracer] instances
     */
    @ExperimentalApi
    public val tracerProvider: TracerProvider

    /**
     * Get the [MeterProvider] used to create new [aws.smithy.kotlin.runtime.telemetry.metrics.Meter] instances
     */
    @ExperimentalApi
    public val meterProvider: MeterProvider

    /**
     * Get the [LoggerProvider] used to create new [aws.smithy.kotlin.runtime.telemetry.logging.Logger] instances
     */
    @ExperimentalApi
    public val loggerProvider: LoggerProvider

    /**
     * Get the [ContextManager] used to get the current [Context]
     */
    @ExperimentalApi
    public val contextManager: ContextManager
}

@OptIn(ExperimentalApi::class)
private object NoOpTelemetryProvider : TelemetryProvider {
    override val meterProvider: MeterProvider = MeterProvider.None
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val loggerProvider: LoggerProvider = LoggerProvider.None
    override val contextManager: ContextManager = ContextManager.None
}
