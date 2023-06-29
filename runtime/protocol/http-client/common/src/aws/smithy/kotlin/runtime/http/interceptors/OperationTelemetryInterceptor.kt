/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.RequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.operation.OperationMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.util.attributesOf
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Captures many of the common metrics associated with executing an operation
 *
 * @param metrics the operation metrics container
 * @param service the name of the service
 * @param operation the name of the operation
 * @param timeSource the time source to use for measuring elapsed time
 */
@OptIn(ExperimentalTime::class)
internal class OperationTelemetryInterceptor(
    private val metrics: OperationMetrics,
    private val service: String,
    private val operation: String,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : HttpInterceptor {

    private var callStart: TimeMark? = null
    private var serializeStart: TimeMark? = null
    private var deserializeStart: TimeMark? = null
    private var signingStart: TimeMark? = null
    private var transmitStart: TimeMark? = null

    private val perRpcAttributes = attributesOf {
        "rpc.service" to service
        "rpc.method" to operation
    }

    override fun readBeforeExecution(context: RequestInterceptorContext<Any>) {
        callStart = timeSource.markNow()
    }

    override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
        val callDuration = callStart?.elapsedNow()?.inWholeMilliseconds ?: return

        // TODO - total requests?

        metrics.rpcCallDuration.record(callDuration, perRpcAttributes, metrics.provider.contextManager.current())

        context.protocolRequest?.body?.contentLength?.let {
            metrics.rpcRequestSize.record(it, perRpcAttributes, metrics.provider.contextManager.current())
        }

        context.protocolResponse?.body?.contentLength?.let {
            metrics.rpcResponseSize.record(it, perRpcAttributes, metrics.provider.contextManager.current())
        }
    }

    override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
        serializeStart = timeSource.markNow()
    }

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        val serializeDuration = serializeStart?.elapsedNow()?.inWholeMilliseconds ?: return
        metrics.serializationDuration.record(serializeDuration, perRpcAttributes, metrics.provider.contextManager.current())
    }

    override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        deserializeStart = timeSource.markNow()
    }

    override fun readAfterDeserialization(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse>) {
        val deserializeDuration = deserializeStart?.elapsedNow()?.inWholeMilliseconds ?: return
        metrics.deserializationDuration.record(deserializeDuration, perRpcAttributes, metrics.provider.contextManager.current())
    }

    override fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        transmitStart = timeSource.markNow()
    }

    override fun readAfterTransmit(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        val serviceCallDuration = transmitStart?.elapsedNow()?.inWholeMilliseconds ?: return
        metrics.serviceCallDuration.record(serviceCallDuration, perRpcAttributes, metrics.provider.contextManager.current())
    }
}
