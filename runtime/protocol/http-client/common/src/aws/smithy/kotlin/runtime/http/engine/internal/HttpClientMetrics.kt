/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.attributesOf

/**
 * Container for common HTTP engine related metrics
 *
 * @param scope the instrumentation scope
 * @param provider the telemetry provider to instrument with
 */
@InternalApi
public class HttpClientMetrics(
    scope: String,
    public val provider: TelemetryProvider,
) {
    private val meter = provider.meterProvider.getOrCreateMeter(scope)

    public val connectionLimit: UpDownCounter = meter.createUpDownCounter("aws.smithy.http.connections.limit", "{connection}", "Max connections configured for the HTTP client")
    public val connectionUsage: UpDownCounter = meter.createUpDownCounter("aws.smithy.http.connections.usage", "{connection}", "Current state of connections (idle, acquired)")
    public val connectionAcquireDuration: LongHistogram = meter.createLongHistogram("aws.smithy.http.connections.acquire_duration", "ms", "The amount of time requests take to acquire a connection from the pool")
    public val requests: UpDownCounter = meter.createUpDownCounter("aws.smithy.http.requests", "{request}", "The current state of requests (queued, in-flight)")
}

/**
 * Common attributes for [HttpClientMetrics]
 */
@InternalApi
public object HttpClientMetricAttributes {
    public val IdleConnection: Attributes = attributesOf { "state" to "idle" }
    public val AcquiredConnection: Attributes = attributesOf { "state" to "acquired" }
    public val QueuedRequest: Attributes = attributesOf { "state" to "queued" }
    public val InFlightRequest: Attributes = attributesOf { "state" to "in-flight" }
}
