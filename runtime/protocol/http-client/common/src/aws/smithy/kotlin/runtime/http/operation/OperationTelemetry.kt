/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.interceptors.OperationTelemetryInterceptor
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.TelemetryProviderContext
import aws.smithy.kotlin.runtime.telemetry.logging.LoggingContextElement
import aws.smithy.kotlin.runtime.telemetry.trace.SpanKind
import aws.smithy.kotlin.runtime.telemetry.trace.TraceSpan
import aws.smithy.kotlin.runtime.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

/**
 * Telemetry parameters used to instrument an operation
 *
 * @property provider the telemetry provider to use to instrument the operation with
 * @property spanName the overall operation span name to use (defaults to <Service.Operation>)
 * @property spanKind the kind of span to create for the operation (defaults to [SpanKind.CLIENT])
 * @property attributes initial attributes to add to the operation span
 * @property metrics the metrics container to report operation metrics to when this operation is invoked (defaults to no-op)
 * @property scope the telemetry scope to instrument with (will default to service name if not configured)
 */
@InternalApi
public class SdkOperationTelemetry {
    public var provider: TelemetryProvider = TelemetryProvider.None
    public var spanName: String? = null
    public var spanKind: SpanKind = SpanKind.CLIENT
    public var attributes: Attributes = emptyAttributes()
    public var metrics: OperationMetrics = OperationMetrics.None
    public var scope: String? = null
}

/**
 * Configure operation telemetry parameters
 */
@InternalApi
public inline fun<I, O> SdkHttpOperationBuilder<I, O>.telemetry(block: SdkOperationTelemetry.() -> Unit) {
    telemetry.apply(block)
}

/**
 * Instrument an operation with telemetry. This should be invoked right when the operation is actually executed
 * as the returned span is in the active state (i.e. current).
 * @return the span for the operation and the additional coroutine context to execute the operation with containing
 * telemetry elements.
 */
@OptIn(ExperimentalTime::class)
internal fun<I, O> SdkHttpOperation<I, O>.instrument(): Pair<TraceSpan, CoroutineContext> {
    val serviceName = checkNotNull(context.serviceName)
    val opName = checkNotNull(context.operationName)

    val scope = telemetry.scope ?: serviceName

    val tracer = telemetry.provider.tracerProvider.getOrCreateTracer(scope)
    val parentCtx = telemetry.provider.contextManager.current()

    val opAttributes = attributesOf {
        "rpc.service" to serviceName
        "rpc.method" to opName
    }

    val initialAttributes = telemetry.attributes.toMutableAttributes().apply {
        merge(opAttributes)
    }

    val rpcName = "$serviceName.$opName"
    val spanName = telemetry.spanName ?: rpcName

    val span = tracer.createSpan(
        spanName,
        initialAttributes,
        telemetry.spanKind,
        parentCtx,
    )

    val telemetryCtx = TelemetryProviderContext(telemetry.provider) +
        LoggingContextElement(
            "rpc" to rpcName,
            "sdkInvocationId" to context.sdkInvocationId,
        )

    context[HttpOperationContext.OperationMetrics] = telemetry.metrics
    context[HttpOperationContext.OperationAttributes] = opAttributes

    // wire up operation level telemetry (other metrics e.g. from http are instrumented elsewhere)
    interceptors.add(OperationTelemetryInterceptor(telemetry.metrics, serviceName, opName))

    return span to telemetryCtx
}
