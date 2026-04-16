/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider

/**
 * An abstract implementation of a telemetry provider. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractTelemetryProvider : TelemetryProvider {
    @ExperimentalApi
    override val meterProvider: MeterProvider = MeterProvider.None

    @ExperimentalApi
    override val tracerProvider: TracerProvider = TracerProvider.None

    @ExperimentalApi
    override val loggerProvider: LoggerProvider = LoggerProvider.None

    @ExperimentalApi
    override val contextManager: ContextManager = ContextManager.None
}
