/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.businessmetrics

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.operation.ExecutionContext

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
public val EndpointOverride: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#EndpointOverride")

/**
 * Emit a business metric to the execution context attributes
 */
@InternalApi
public fun ExecutionContext.emitBusinessMetric(metric: BusinessMetric) {
    if (this.attributes.contains(BusinessMetrics)) {
        this.attributes[BusinessMetrics].add(metric.identifier)
    } else {
        this.attributes[BusinessMetrics] = mutableSetOf(metric.identifier)
    }
}

/**
 * Removes a business metric from the execution context attributes
 */
@InternalApi
public fun ExecutionContext.removeBusinessMetric(metric: BusinessMetric) {
    if (this.attributes.contains(BusinessMetrics)) {
        this.attributes[BusinessMetrics].remove(metric.identifier)
    }
}

/**
 * Checks if a business metric exists in the execution context attributes
 */
@InternalApi
public fun ExecutionContext.containsBusinessMetric(metric: BusinessMetric): Boolean =
    (this.attributes.contains(BusinessMetrics)) && this.attributes[BusinessMetrics].contains(metric.identifier)

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
    ENDPOINT_OVERRIDE("N"),
    ACCOUNT_ID_ENDPOINT("O"),
    ACCOUNT_ID_MODE_PREFERRED("P"), // TODO: Emit this metric
    ACCOUNT_ID_MODE_DISABLED("Q"), // TODO: Emit this metric
    ACCOUNT_ID_MODE_REQUIRED("R"), // TODO: Emit this metric
    SIGV4A_SIGNING("S"),
    RESOLVED_ACCOUNT_ID("T"), // TODO: Emit this metric
}
