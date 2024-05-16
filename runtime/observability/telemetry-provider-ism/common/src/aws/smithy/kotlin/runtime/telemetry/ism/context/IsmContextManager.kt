/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism.context

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.http.operation.OperationAttributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.context.Scope
import aws.smithy.kotlin.runtime.telemetry.ism.metrics.MetricRecord
import aws.smithy.kotlin.runtime.telemetry.ism.metrics.ScopeMetrics

public interface SpanListener {
    public fun onNewSpan(parentContext: Context?, name: String, attributes: Attributes): Context
    public fun onCloseSpan(context: Context)
}

internal interface ContextStorage {
    fun get(): Context
    fun getAndSet(value: Context): Context
    fun requireAndSet(expect: Context, update: Context)
}

internal expect fun ContextStorage(initialContext: Context): ContextStorage

public class IsmContextManager private constructor() : ContextManager {
    public companion object {
        public fun createWithScopeListener(): Pair<IsmContextManager, SpanListener> {
            val manager = IsmContextManager()
            val listener = object : SpanListener {
                override fun onNewSpan(parentContext: Context?, name: String, attributes: Attributes) =
                    manager.onNewSpan(parentContext, name, attributes)

                override fun onCloseSpan(context: Context) = manager.onCloseSpan(context)
            }
            return manager to listener
        }
    }

    private val rootContext = object : HierarchicalContext(null) { }
    private val storage = ContextStorage(rootContext)

    override fun current(): Context = storage.get()

    private fun onNewSpan(parentContext: Context?, name: String, attributes: Attributes): Context =
        when (parentContext) {
            rootContext -> {
                val service = attributes.getOrNull(OperationAttributes.RpcService)
                val operation = attributes.getOrNull(OperationAttributes.RpcOperation)
                val sdkInvocationId = attributes.getOrNull(OperationAttributes.AwsInvocationId)
                if (service != null && operation != null && sdkInvocationId != null) {
                    OperationContext(service, operation, sdkInvocationId)
                } else {
                    OtherContext(parentContext)
                }
            }

            is OperationContext, is ChildContext -> ChildContext(parentContext)

            else -> OtherContext(parentContext)
        }

    private fun onCloseSpan(context: Context) {
        TODO()
    }

    private abstract inner class HierarchicalContext(val parent: Context?) : Context {
        override fun makeCurrent(): Scope {
            storage.requireAndSet(parent ?: Context.None, this)
            return IsmScope(this)
        }
    }

    private inner class OperationContext(
        val service: String,
        val operation: String,
        val sdkInvocationId: String,
        val records: MutableList<MetricRecord<*>> = mutableListOf(),
        val childScopes: MutableMap<String, ScopeMetrics> = mutableMapOf(),
    ) : HierarchicalContext(rootContext)

    private inner class ChildContext(
        parent: Context,
        val records: MutableList<MetricRecord<*>> = mutableListOf(),
        val childScopes: MutableMap<String, ScopeMetrics> = mutableMapOf(),
    ) : HierarchicalContext(parent)

    private inner class OtherContext(parent: Context?) : HierarchicalContext(parent)

    private inner class IsmScope(val context: Context) : Scope {
        override fun close() {
            storage.requireAndSet(context, Context.None)
        }
    }
}
