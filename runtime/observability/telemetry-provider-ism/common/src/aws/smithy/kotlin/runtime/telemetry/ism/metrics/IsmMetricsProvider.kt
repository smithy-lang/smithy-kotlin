/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism.metrics

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.time.Instant

public interface MetricRecord<T> {
    // Group these into higher-level type?
    public val name: String
    public val units: String?
    public val description: String?

    public val value: T
    public val attributes: Attributes
    public val context: Context

    public val timestamp: Instant
}

private data class MetricRecordImpl<T>(
    override val name: String,
    override val units: String?,
    override val description: String?,
    override val value: T,
    override val attributes: Attributes,
    override val context: Context,
    override val timestamp: Instant,
) : MetricRecord<T>

public fun <T> MetricRecord(
    name: String,
    units: String?,
    description: String?,
    value: T,
    attributes: Attributes,
    context: Context,
    timestamp: Instant,
): MetricRecord<T> = MetricRecordImpl(name, units, description, value, attributes, context, timestamp)

public interface ScopeMetrics {
    public val records: Map<String, List<MetricRecord<*>>> // Feels like this should be keyed by typed attributes
    public val childScopes: Map<String, ScopeMetrics>
}

private data class ScopeMetricsImpl(
    override val records: Map<String, List<MetricRecord<*>>>,
    override val childScopes: Map<String, ScopeMetrics>,
) : ScopeMetrics

public fun ScopeMetrics(
    records: Map<String, List<MetricRecord<*>>>,
    childScopes: Map<String, ScopeMetrics>,
): ScopeMetrics =
    ScopeMetricsImpl(records, childScopes)

public interface OperationMetrics : ScopeMetrics {
    public val service: String
    public val operation: String
    public val sdkInvocationId: String
}

private data class OperationMetricsImpl(
    override val service: String,
    override val operation: String,
    override val sdkInvocationId: String,
    override val records: Map<String, List<MetricRecord<*>>>,
    override val childScopes: Map<String, ScopeMetrics>,
) : OperationMetrics

public fun OperationMetrics(
    service: String,
    operation: String,
    sdkInvocationId: String,
    records: Map<String, List<MetricRecord<*>>>,
    childScopes: Map<String, ScopeMetrics>,
): OperationMetrics = OperationMetricsImpl(service, operation, sdkInvocationId, records, childScopes)

@ExperimentalApi
public interface OperationMetricsCollector {
    public fun onOperationMetrics(metrics: OperationMetrics)
}

@ExperimentalApi
public class IsmMetricsProvider(private val collector: OperationMetricsCollector) : MeterProvider {
    override fun getOrCreateMeter(scope: String): Meter {
        TODO("Not yet implemented")
    }
}

@ExperimentalApi
private class OperationMeter(private val collector: OperationMetricsCollector) {

}
