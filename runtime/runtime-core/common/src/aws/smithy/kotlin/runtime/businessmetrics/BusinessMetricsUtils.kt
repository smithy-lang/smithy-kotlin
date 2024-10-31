/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.businessmetrics

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.*

/**
 * Keeps track of all business metrics along an operations execution
 */
@InternalApi
public val BusinessMetrics: AttributeKey<MutableSet<String>> = AttributeKey("aws.sdk.kotlin#BusinessMetrics")

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
 * Emits a business metric into [MutableAttributes]
 * @param identifier The identifier of the [BusinessMetric] to be emitted.
 */
@InternalApi
public fun MutableAttributes.emitBusinessMetric(identifier: String) {
    if (this.contains(BusinessMetrics)) {
        this[BusinessMetrics].add(identifier)
    } else {
        this[BusinessMetrics] = mutableSetOf(identifier)
    }
}

/**
 * Emits a business metric into [MutableAttributes]
 * @param metric The [BusinessMetric] to be emitted.
 */
@InternalApi
public fun MutableAttributes.emitBusinessMetric(metric: BusinessMetric): Unit = this.emitBusinessMetric(metric.identifier)

/**
 * Emits business metrics into [MutableAttributes]
 * @param metrics The [BusinessMetric]s to be emitted.
 */
@InternalApi
public fun MutableAttributes.emitBusinessMetrics(metrics: Set<BusinessMetric>): Unit =
    metrics.forEach { emitBusinessMetric(it) }

/**
 * Removes a business metric from the [MutableAttributes]
 */
@InternalApi
public fun MutableAttributes.removeBusinessMetric(metric: BusinessMetric) {
    if (this.contains(BusinessMetrics)) {
        this[BusinessMetrics].remove(metric.identifier)
    }
}

/**
 * Checks if a business metric exists in the [Attributes]
 */
@InternalApi
public fun Attributes.containsBusinessMetric(metric: BusinessMetric): Boolean =
    (this.contains(BusinessMetrics)) && this[BusinessMetrics].contains(metric.identifier)

/**
 * Valid business metrics
 */
public interface BusinessMetric {
    public val identifier: String
}

/**
 * Generic business metrics
 */
@InternalApi
public enum class SmithyBusinessMetric(public override val identifier: String) : BusinessMetric {
    WAITER("B"), // TODO: Emit this metric
    PAGINATOR("C"), // TODO: Emit this metric
    RETRY_MODE_STANDARD("E"),
    RETRY_MODE_ADAPTIVE("F"),
    GZIP_REQUEST_COMPRESSION("L"),
    PROTOCOL_RPC_V2_CBOR("M"),
    SERVICE_ENDPOINT_OVERRIDE("N"),
    ACCOUNT_ID_BASED_ENDPOINT("O"),
    SIGV4A_SIGNING("S"),
}
