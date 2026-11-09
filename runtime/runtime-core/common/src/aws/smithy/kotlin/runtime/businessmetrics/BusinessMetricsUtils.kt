/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.businessmetrics

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.MutableAttributes
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Keeps track of all business metrics along an operations execution
 */
@InternalApi
public val BusinessMetrics: AttributeKey<MutableSet<BusinessMetric>> = AttributeKey("aws.smithy.kotlin#BusinessMetrics")

/**
 * The account ID in an account ID based endpoint
 */
@InternalApi
public val AccountIdBasedEndpointAccountId: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#AccountIdBasedEndpointAccountId")

/**
 * If an endpoint is "service endpoint override" based
 */
@InternalApi
public val ServiceEndpointOverride: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#ServiceEndpointOverride")

/**
 * Emit a business metric to the execution context attributes
 */
@InternalApi
public fun ExecutionContext.emitBusinessMetric(metric: BusinessMetric) {
    if (this.attributes.contains(BusinessMetrics)) {
        this.attributes[BusinessMetrics].add(metric)
    } else {
        this.attributes[BusinessMetrics] = mutableSetOf(metric)
    }
}

/**
 * Emit a business metric to the mutable attributes
 */
@InternalApi
public fun MutableAttributes.emitBusinessMetric(metric: BusinessMetric) {
    if (this.contains(BusinessMetrics)) {
        this[BusinessMetrics].add(metric)
    } else {
        this[BusinessMetrics] = mutableSetOf(metric)
    }
}

/**
 * Removes a business metric from the execution context attributes
 */
@InternalApi
public fun ExecutionContext.removeBusinessMetric(metric: BusinessMetric) {
    if (this.attributes.contains(BusinessMetrics)) {
        this.attributes[BusinessMetrics].remove(metric)
    }
}

/**
 * Checks if a business metric exists in the execution context attributes
 */
@InternalApi
public fun ExecutionContext.containsBusinessMetric(metric: BusinessMetric): Boolean = (this.attributes.contains(BusinessMetrics)) && this.attributes[BusinessMetrics].contains(metric)

/**
 * Valid business metrics
 */
public interface BusinessMetric {
    public val identifier: String
}

/**
 * A component whose use implies a set of business metrics, e.g. a telemetry provider backed by a specific library.
 * When a component implements this interface, its [businessMetrics] replace any metrics the SDK would otherwise infer
 * from the component's presence.
 *
 * Detection is by the type of the component instance itself. A component which wraps or decorates another must
 * implement this interface itself to forward the wrapped component's metrics.
 */
@InternalApi
public interface BusinessMetricsSource {
    /**
     * The business metrics to emit for each operation that uses this component
     */
    public val businessMetrics: Set<BusinessMetric>
}

/**
 * Coroutine scoped business metrics which apply to every operation invoked directly within the scope, e.g. all the
 * operations called by a waiter or paginator.
 *
 * Operations read this element when they start and then run with it cleared, so that it does not leak into
 * operations they invoke internally, such as a credentials provider calling STS.
 *
 * @param metrics The business metrics to emit for each operation invoked in this scope
 */
@InternalApi
public class ScopedBusinessMetricsContext(
    public val metrics: Set<BusinessMetric>,
) : AbstractCoroutineContextElement(ScopedBusinessMetricsContext) {
    public companion object Key : CoroutineContext.Key<ScopedBusinessMetricsContext>
    override fun toString(): String = "ScopedBusinessMetricsContext($metrics)"
}

/**
 * Invoke [block] such that every operation called directly within it emits [metrics], in addition to any scoped
 * business metrics already present in the current coroutine context.
 */
@InternalApi
public suspend fun <R> withScopedBusinessMetrics(
    vararg metrics: BusinessMetric,
    block: suspend () -> R,
): R {
    val scopedMetrics = currentCoroutineContext()[ScopedBusinessMetricsContext]?.metrics.orEmpty() + metrics
    return withContext(ScopedBusinessMetricsContext(scopedMetrics)) {
        block()
    }
}

/**
 * Generic business metrics
 */
@InternalApi
public enum class SmithyBusinessMetric(public override val identifier: String) : BusinessMetric {
    WAITER("B"),
    PAGINATOR("C"),
    RETRY_MODE_STANDARD("E"),
    RETRY_MODE_ADAPTIVE("F"),
    GZIP_REQUEST_COMPRESSION("L"),
    PROTOCOL_RPC_V2_CBOR("M"),
    SERVICE_ENDPOINT_OVERRIDE("N"),
    ACCOUNT_ID_BASED_ENDPOINT("O"),
    ACCOUNT_ID_MODE_PREFERRED("P"),
    ACCOUNT_ID_MODE_DISABLED("Q"),
    ACCOUNT_ID_MODE_REQUIRED("R"),
    SIGV4A_SIGNING("S"),
    RESOLVED_ACCOUNT_ID("T"),
    FLEXIBLE_CHECKSUMS_REQ_CRC32("U"),
    FLEXIBLE_CHECKSUMS_REQ_CRC32C("V"),
    FLEXIBLE_CHECKSUMS_REQ_SHA1("X"),
    FLEXIBLE_CHECKSUMS_REQ_SHA256("Y"),
    FLEXIBLE_CHECKSUMS_REQ_WHEN_SUPPORTED("Z"),
    FLEXIBLE_CHECKSUMS_REQ_WHEN_REQUIRED("a"),
    FLEXIBLE_CHECKSUMS_RES_WHEN_SUPPORTED("b"),
    FLEXIBLE_CHECKSUMS_RES_WHEN_REQUIRED("c"),
    BEARER_SERVICE_ENV_VARS("3"),
    OBSERVABILITY_TRACING("4"),
    OBSERVABILITY_METRICS("5"),
    OBSERVABILITY_OTEL_TRACING("6"),
    OBSERVABILITY_OTEL_METRICS("7"),
    ;

    override fun toString(): String = identifier
}
