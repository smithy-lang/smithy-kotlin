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
public fun ExecutionContext.containsBusinessMetric(metric: BusinessMetric): Boolean =
    (this.attributes.contains(BusinessMetrics)) && this.attributes[BusinessMetrics].contains(metric)

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
    FLEXIBLE_CHECKSUMS_REQ_CRC32("U"),
    FLEXIBLE_CHECKSUMS_REQ_CRC32C("V"),
    FLEXIBLE_CHECKSUMS_REQ_SHA1("X"),
    FLEXIBLE_CHECKSUMS_REQ_SHA256("Y"),
    FLEXIBLE_CHECKSUMS_REQ_WHEN_SUPPORTED("Z"),
    FLEXIBLE_CHECKSUMS_REQ_WHEN_REQUIRED("a"),
    FLEXIBLE_CHECKSUMS_RES_WHEN_SUPPORTED("b"),
    FLEXIBLE_CHECKSUMS_RES_WHEN_REQUIRED("c"),
    ;

    override fun toString(): String = identifier
}
