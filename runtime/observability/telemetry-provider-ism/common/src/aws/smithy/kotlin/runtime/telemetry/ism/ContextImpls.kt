package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.Scope
import aws.smithy.kotlin.runtime.telemetry.trace.SpanContext
import kotlin.random.Random

internal sealed class HierarchicalContext(
    val name: String,
    val parent: Context?,
    val spanContext: SpanContext,
) : Context {
    constructor(name: String, parent: Context?) : this(
        name,
        parent,
        parent?.spanContext()?.childSpan() ?: SpanContext.Invalid,
    )

    open val records: MutableList<MetricRecord<*>> = mutableListOf()
    open val children: MutableMap<String, ChildContext> = mutableMapOf()

    override fun makeCurrent(): Scope = IsmScope(this)
}

internal class RootContext : HierarchicalContext("root", null, IsmSpanContext.fromScratch()) {
    override val records: MutableList<MetricRecord<*>>
        get() = mutableListOf()

    override val children: MutableMap<String, ChildContext>
        get() = mutableMapOf()
}

internal class OperationContext(
    name: String,
    rootContext: RootContext,
    val service: String,
    val operation: String,
    val sdkInvocationId: String,
) : HierarchicalContext(name, rootContext)

internal class ChildContext(name: String, parent: HierarchicalContext) : HierarchicalContext(name, parent)

internal class OtherContext(name: String, parent: Context?) : HierarchicalContext(name, parent)

private class IsmScope(val context: Context) : Scope {
    override fun close() = Unit
}

private data class IsmSpanContext(
    override val traceId: String,
    override val spanId: String,
    override val isRemote: Boolean,
) : SpanContext {
    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        private fun nextHexString(byteLength: Int) = Random.nextBytes(byteLength).toHexString()

        private fun nextSpanId() = nextHexString(8)
        private fun nextTraceId() = nextHexString(16)

        fun fromScratch() = IsmSpanContext(nextTraceId(), nextSpanId(), false)

        private val zeroChar = setOf(' ', '0')
        fun isValid(id: String) = id.any { it !in zeroChar }
    }

    override val isValid = isValid(traceId) && isValid(spanId)

    fun childSpan() = IsmSpanContext(traceId, nextSpanId(), isRemote)
}

private fun Context.spanContext() = when (this) {
    is HierarchicalContext -> spanContext
    else -> SpanContext.Invalid
}

private fun SpanContext.childSpan() = when (this) {
    is IsmSpanContext -> childSpan()
    else -> SpanContext.Invalid
}
