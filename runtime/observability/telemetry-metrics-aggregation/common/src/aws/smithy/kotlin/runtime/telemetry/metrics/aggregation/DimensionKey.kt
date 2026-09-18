/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes

/**
 * A stable, hashable projection of an [Attributes] set onto the configured dimension allowlist.
 *
 * [Attributes] is an interface whose contract promises no structural equality, so using it directly as
 * the aggregation store's key would duplicate or intermittently merge series depending on the
 * implementation passed in. Projecting onto the allowlist at the same time is the first line of
 * cardinality defence: an attribute that never becomes a dimension cannot grow cardinality.
 *
 * Internal because exporters receive the plain `List<Dimension>` on [MetricPoint], which leaves the
 * projection strategy free to change.
 */
internal class DimensionKey(
    val dimensions: List<Dimension>,
) {
    // Hand-written rather than a `data class` because `copy()` would offer a construction path that
    // bypasses `dimensionsOf`, whose sorting is what makes two logically equal keys compare equal.
    override fun equals(other: Any?): Boolean = other is DimensionKey && other.dimensions == dimensions
    override fun hashCode(): Int = dimensions.hashCode()

    /** Rendered as `k=v,k=v` - this is what appears in cardinality-overflow warnings. */
    override fun toString(): String = dimensions.joinToString(",") { "${it.name}=${it.value}" }

    companion object {
        /**
         * The bucket every measurement lands in once the per-instrument cardinality cap saturates. A
         * visible `overflow=true` dimension rather than an empty key, so the condition is evident on the
         * backend's console instead of looking like wrong data.
         */
        val Overflow: DimensionKey = DimensionKey(listOf(Dimension("overflow", "true")))
    }
}

/**
 * Build a [DimensionKey] from [attributes], keeping only names present in [allowed].
 *
 * @param attributes the attributes supplied at the call site.
 * @param allowed dimension names to retain. Two measurements differing only in a non-allowlisted
 *   attribute aggregate together.
 * @return a key whose dimensions are sorted by name.
 */
internal fun dimensionsOf(attributes: Attributes, allowed: Set<String>): DimensionKey {
    if (attributes.isEmpty || allowed.isEmpty()) return DimensionKey(emptyList())

    val dims = attributes.keys
        .filter { it.name in allowed }
        .mapNotNull { key ->
            // Iterating `keys` erases the type argument, but the value is only ever stringified.
            @Suppress("UNCHECKED_CAST")
            val value = attributes.getOrNull(key as AttributeKey<Any>) ?: return@mapNotNull null
            Dimension(key.name, value.toString())
        }
        // `Attributes` makes no ordering guarantee, so without this the same series could split in two
        // depending on population order.
        .sortedBy { it.name }

    return DimensionKey(dims)
}

/**
 * Bounds the number of distinct attribute sets tracked for a single instrument.
 *
 * Some SDK metric attributes come from remote responses - error codes especially - so an unguarded
 * registry's heap and backend spend are bounded only by input the application does not control.
 *
 * Saturating collapses new attribute sets into [DimensionKey.Overflow] rather than dropping them, so
 * aggregate totals stay correct and only the breakdown is lost. This bounds attribute *sets*; distinct
 * values within one set are bounded separately by [DistributionAggregator].
 *
 * Not thread-safe: callers hold the owning instrument's lock.
 *
 * @param max maximum distinct attribute sets admitted before overflow bucketing begins.
 */
internal class CardinalityGuard(private val max: Int) {
    private val seen = mutableSetOf<DimensionKey>()

    /**
     * @return [key] if it is already tracked or if there is room for it; otherwise
     *   [DimensionKey.Overflow].
     */
    fun admit(key: DimensionKey): DimensionKey = when {
        // First, so an already-admitted key is never re-counted against the cap.
        key in seen -> key
        seen.size < max -> key.also { seen.add(it) }
        else -> DimensionKey.Overflow
    }

    /**
     * Forget all admitted keys. Not called per collection cycle: that would turn the cap into a
     * per-interval allowance and defeat the cost control. For tests and a future explicit-reset API.
     */
    fun reset(): Unit = seen.clear()
}
