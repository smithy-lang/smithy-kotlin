/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.RequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.collections.merge
import aws.smithy.kotlin.runtime.collections.mutableAttributesOf
import aws.smithy.kotlin.runtime.collections.takeOrNull
import aws.smithy.kotlin.runtime.http.engine.EngineAttributes
import aws.smithy.kotlin.runtime.http.operation.OperationAttributes
import aws.smithy.kotlin.runtime.http.operation.OperationMetrics
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.recordSeconds
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
@OptIn(ExperimentalApi::class)
internal class OperationTelemetryInterceptor(
    private val metrics: OperationMetrics,
    private val service: String,
    private val operation: String,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : HttpInterceptor {

    private var callStart: TimeMark? = null
    private var serializeStart: TimeMark? = null
    private var deserializeStart: TimeMark? = null
    private var attemptStart: TimeMark? = null
    private var attempts = 0

    private val perRpcAttributes = attributesOf {
        OperationAttributes.RpcService to service
        OperationAttributes.RpcOperation to operation
    }

    private val RequestInterceptorContext<*>.metricsContext: Context
        get() = metrics.provider.contextManager.current(executionContext.coroutineContext)

    override fun readBeforeExecution(context: RequestInterceptorContext<Any>) {
        callStart = timeSource.markNow()
    }

    override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
        val metricsCtx = context.metricsContext

        callStart?.elapsedNow()?.let { callDuration ->
            metrics.rpcCallDuration.recordSeconds(callDuration, perRpcAttributes, metricsCtx)
        }

        context.response.exceptionOrNull()?.let { ex ->
            val exType = ex::class.simpleName
            val errorAttributes = if (exType != null) {
                mutableAttributesOf { "exception.type" to exType }.also {
                    it.merge(perRpcAttributes)
                }
            } else {
                perRpcAttributes
            }
            metrics.rpcErrors.add(1L, errorAttributes, metricsCtx)
        }
    }

    override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
        serializeStart = timeSource.markNow()
    }

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        val serializeDuration = serializeStart?.elapsedNow() ?: return
        metrics.serializationDuration.recordSeconds(serializeDuration, perRpcAttributes, context.metricsContext)
    }

    override fun readAfterAttempt(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>) {
        val metricsCtx = context.metricsContext

        metrics.rpcAttempts.add(1L, perRpcAttributes, metricsCtx)
        attempts++

        val attemptDuration = attemptStart?.elapsedNow() ?: return
        metrics.rpcAttemptDuration.recordSeconds(attemptDuration, perRpcAttributes, metricsCtx)

        context.executionContext.takeOrNull(EngineAttributes.TimeToFirstByte)?.let { ttfb ->
            metrics.rpcAttemptOverheadDuration.recordSeconds(attemptDuration - ttfb, perRpcAttributes)
        }
    }

    override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        deserializeStart = timeSource.markNow()
    }

    override fun readAfterDeserialization(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse>) {
        val deserializeDuration = deserializeStart?.elapsedNow() ?: return
        metrics.deserializationDuration.recordSeconds(deserializeDuration, perRpcAttributes, context.metricsContext)
    }

    override fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        attemptStart = timeSource.markNow()
    }
}
