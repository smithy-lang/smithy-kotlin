/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram

/**
 * Container for common operation/call metrics
 *
 * @param scope the instrumentation scope
 * @param provider the telemetry provider to instrument with
 */
@InternalApi
public class OperationMetrics(
    scope: String,
    public val provider: TelemetryProvider,
) {
    private val meter = provider.meterProvider.getOrCreateMeter(scope)
    internal companion object {
        val None: OperationMetrics = OperationMetrics("NoOp", TelemetryProvider.None)
    }

    public val rpcCallDuration: LongHistogram = meter.createLongHistogram("smithy.client.duration", "ms", "Overall call duration including retries")
    public val rpcRequestSize: LongHistogram = meter.createLongHistogram("smithy.client.request.size", "By", "Size of the serialized request message")
    public val rpcResponseSize: LongHistogram = meter.createLongHistogram("smithy.client.response.size", "By", "Size of the serialized response message")
    public val serviceCallDuration: LongHistogram = meter.createLongHistogram("smithy.client.service_call_duration", "ms", "The time it takes to connect to the service, send the request, and receive the HTTP status code and headers from the response")
    public val serializationDuration: LongHistogram = meter.createLongHistogram("smithy.client.serialization_duration", "ms", "The time it takes to serialize a request message body")
    public val deserializationDuration: LongHistogram = meter.createLongHistogram("smithy.client.deserialization_duration", "ms", "The time it takes to deserialize a response message body")
    public val resolveEndpointDuration: LongHistogram = meter.createLongHistogram("smithy.client.resolve_endpoint_duration", "ms", "The time it takes to resolve an endpoint for a request")
    public val resolveIdentityDuration: LongHistogram = meter.createLongHistogram("smithy.client.auth.resolve_identity_duration", "ms", "The time it takes to resolve an identity for signing a request")
    public val signingDuration: LongHistogram = meter.createLongHistogram("smithy.client.auth.signing_duration", "ms", "The time it takes to sign a request")
}
