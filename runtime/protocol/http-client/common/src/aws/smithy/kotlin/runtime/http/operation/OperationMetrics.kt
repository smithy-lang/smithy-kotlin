/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter

/**
 * Container for common operation/call metrics
 *
 * @param scope the instrumentation scope
 * @param provider the telemetry provider to instrument with
 */
@OptIn(ExperimentalApi::class)
@InternalApi
public class OperationMetrics(
    scope: String,
    public val provider: TelemetryProvider,
) {
    private val meter = provider.meterProvider.getOrCreateMeter(scope)
    internal companion object {
        val None: OperationMetrics = OperationMetrics("NoOp", TelemetryProvider.None)
    }

    public val rpcCallDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.duration", "s", "Overall call duration including retries")
    public val rpcAttempts: MonotonicCounter = meter.createMonotonicCounter("smithy.client.attempts", "{attempt}", "The number of attempts for an operation")
    public val rpcErrors: MonotonicCounter = meter.createMonotonicCounter("smithy.client.errors", "{error}", "The number of errors for an operation")
    public val rpcRetryCount: MonotonicCounter = meter.createMonotonicCounter("smithy.client.retries", "{count}", "The number of retries for an operation")
    public val rpcRequestSize: LongHistogram = meter.createLongHistogram("smithy.client.request.size", "By", "Size of the serialized request message")
    public val rpcResponseSize: LongHistogram = meter.createLongHistogram("smithy.client.response.size", "By", "Size of the serialized response message")
    public val rpcAttemptDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.attempt_duration", "s", "The time it takes to connect to the service, send the request, and receive the HTTP status code and headers from the response for an operation")
    public val serializationDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.serialization_duration", "s", "The time it takes to serialize a request message body")
    public val deserializationDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.deserialization_duration", "s", "The time it takes to deserialize a response message body")
    public val resolveEndpointDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.resolve_endpoint_duration", "s", "The time it takes to resolve an endpoint for a request")
    public val resolveIdentityDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.auth.resolve_identity_duration", "s", "The time it takes to resolve an identity for signing a request")
    public val signingDuration: DoubleHistogram = meter.createDoubleHistogram("smithy.client.auth.signing_duration", "s", "The time it takes to sign a request")
}
