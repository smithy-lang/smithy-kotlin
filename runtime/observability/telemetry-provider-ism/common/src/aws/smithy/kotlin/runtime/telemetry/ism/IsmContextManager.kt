/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.http.operation.OperationAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.context.telemetryContext
import kotlin.coroutines.CoroutineContext

internal interface MetricListener {
    fun onMetrics(context: Context, metrics: MetricRecord<*>)
}

internal interface SpanListener {
    fun onNewSpan(parentContext: Context?, name: String, attributes: Attributes): Context
    fun onCloseSpan(context: Context)
}

@OptIn(ExperimentalApi::class)
internal class IsmContextManager internal constructor(private val sink: IsmMetricSink) : ContextManager {
    private val rootContext = RootContext()

    override fun current(ctx: CoroutineContext): Context =
        ctx.telemetryContext.takeIf { it != Context.None } ?: rootContext

    internal val metricListener = object : MetricListener {
        override fun onMetrics(context: Context, metrics: MetricRecord<*>) {
            println("Listener received metrics on $context: $metrics")
            when (context) {
                is OperationContext -> context.records += metrics
                is ChildContext -> context.records += metrics
            }
        }
    }

    internal val spanListener = object : SpanListener {
        override fun onNewSpan(parentContext: Context?, name: String, attributes: Attributes): Context {
            println("Listener received new span on $parentContext: $name")
            return when (parentContext) {
                null, rootContext -> {
                    val service = attributes.getOrNull(OperationAttributes.RpcService)
                    val operation = attributes.getOrNull(OperationAttributes.RpcOperation)
                    val sdkInvocationId = attributes.getOrNull(OperationAttributes.AwsInvocationId)

                    if (service == null || operation == null || sdkInvocationId == null) {
                        OtherContext(name, parentContext)
                    } else {
                        OperationContext(name, rootContext, service, operation, sdkInvocationId)
                    }
                }

                is HierarchicalContext -> ChildContext(name, parentContext)

                else -> OtherContext(name, parentContext)
            }
        }

        override fun onCloseSpan(context: Context) {
            when (context) {
                is OperationContext -> publish(context)
                is ChildContext -> (context.parent as? HierarchicalContext)?.let { parentContext ->
                    parentContext.children += context.name to context
                }
            }
        }
    }

    private fun publish(context: OperationContext) {
        val opMetrics = OperationMetrics(
            context.service,
            context.operation,
            context.sdkInvocationId,
            context.records.toList(),
            context.children.mapValues { (_, child) -> child.toScopeMetrics() }
        )
        sink.onInvocationComplete(opMetrics)
    }
}

private fun ChildContext.toScopeMetrics(): ScopeMetrics = ScopeMetrics(
    records.toList(),
    children.mapValues { (_, child) -> child.toScopeMetrics() }
)
