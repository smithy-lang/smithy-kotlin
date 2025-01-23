package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant

public interface MetricRecord<T> {
    // Group these into higher-level type?
    public val name: String
    public val units: String?
    public val description: String?

    public val value: T
    public val attributes: Attributes

    public val timestamp: Instant
}

private data class MetricRecordImpl<T>(
    override val name: String,
    override val units: String?,
    override val description: String?,
    override val value: T,
    override val attributes: Attributes,
    override val timestamp: Instant,
) : MetricRecord<T>

public fun <T> MetricRecord(
    name: String,
    units: String?,
    description: String?,
    value: T,
    attributes: Attributes,
    timestamp: Instant,
): MetricRecord<T> = MetricRecordImpl(name, units, description, value, attributes, timestamp)

public interface ScopeMetrics {
    public val records: List<MetricRecord<*>> // Feels like this should be keyed by typed attributes
    public val childScopes: Map<String, ScopeMetrics>
}

private data class ScopeMetricsImpl(
    override val records: List<MetricRecord<*>>,
    override val childScopes: Map<String, ScopeMetrics>,
) : ScopeMetrics

public fun ScopeMetrics(
    records: List<MetricRecord<*>>,
    childScopes: Map<String, ScopeMetrics>,
): ScopeMetrics = ScopeMetricsImpl(records, childScopes)

public interface OperationMetrics : ScopeMetrics {
    public val service: String
    public val operation: String
    public val sdkInvocationId: String
}

private data class OperationMetricsImpl(
    override val service: String,
    override val operation: String,
    override val sdkInvocationId: String,
    override val records: List<MetricRecord<*>>,
    override val childScopes: Map<String, ScopeMetrics>,
) : OperationMetrics

public fun OperationMetrics(
    service: String,
    operation: String,
    sdkInvocationId: String,
    records: List<MetricRecord<*>>,
    childScopes: Map<String, ScopeMetrics>,
): OperationMetrics = OperationMetricsImpl(service, operation, sdkInvocationId, records, childScopes)
