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
public val businessMetrics: AttributeKey<MutableSet<String>> = AttributeKey("aws.sdk.kotlin#BusinessMetrics")

/**
 * The account ID in an account ID based endpoint
 */
@InternalApi
public val accountIdBasedEndPointAccountId: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#AccountIdBasedEndpointAccountId")

/**
 * If an endpoint is service endpoint override based
 */
@InternalApi
public val serviceEndpointOverride: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#ServiceEndpointOverride")

/**
 * Emit a business metric to the execution context attributes
 */
@InternalApi
public fun ExecutionContext.emitBusinessMetric(metric: BusinessMetrics) {
    if (this.attributes.contains(businessMetrics)) {
        this.attributes[businessMetrics].add(metric.identifier)
    } else {
        this.attributes[businessMetrics] = mutableSetOf(metric.identifier)
    }
}

/**
 * Removes a business metric from the execution context attributes
 */
@InternalApi
public fun ExecutionContext.removeBusinessMetric(metric: BusinessMetrics) {
    if (this.attributes.contains(businessMetrics)) {
        this.attributes[businessMetrics].remove(metric.identifier)
    }
}

/**
 * Checks if a business metric exists in the execution context attributes
 */
@InternalApi
public fun ExecutionContext.containsBusinessMetric(metric: BusinessMetrics): Boolean =
    (this.attributes.contains(businessMetrics)) && this.attributes[businessMetrics].contains(metric.identifier)

/**
 * All the valid business metrics along with their identifiers
 */
@InternalApi
public enum class BusinessMetrics(public val identifier: String) {
    S3_EXPRESS_BUCKET("A"),
    GZIP_REQUEST_COMPRESSION("B"),
    RETRY_MODE_LEGACY("C"),
    RETRY_MODE_STANDARD("D"),
    RETRY_MODE_ADAPTIVE("E"),
    SIGV4A_SIGNING("F"),
    SERVICE_ENDPOINT_OVERRIDE("G"),
    ACCOUNT_ID_BASED_ENDPOINT("H"),
}
