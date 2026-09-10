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
 * ### Why this type exists at all
 *
 * The aggregation store is a map keyed by attribute set, so the key must have reliable
 * `equals`/`hashCode`. [Attributes] does **not** guarantee either — it is an interface with `keys`,
 * `getOrNull`, `contains`, and `isEmpty`, and nothing in its contract promises structural equality.
 * Using [Attributes] directly as a map key would therefore work or silently fail depending on the
 * implementation handed in, producing either duplicated series or, worse, series that merge only
 * sometimes. Deriving an explicit key removes that dependency entirely.
 *
 * Projecting onto an allowlist at the same time serves two further purposes: backends bill per
 * unique dimension combination, and it is the first line of cardinality defence — an attribute that
 * never becomes a dimension cannot contribute to cardinality growth no matter how many distinct
 * values it takes.
 *
 * Internal: this is the aggregation store's key, not part of the exported shape. What reaches a
 * [MetricExporter] is the plain `List<Dimension>` on [MetricPoint], so exporters never need this type and
 * keeping it out of the public API leaves the projection strategy free to change.
 */
internal class DimensionKey(
    val dimensions: List<Dimension>,
) {
    // Hand-written rather than a `data class` because `copy()` would offer a construction path that
    // bypasses `dimensionsOf`, whose sorting is what makes two logically equal keys compare equal.
    override fun equals(other: Any?): Boolean = other is DimensionKey && other.dimensions == dimensions
    override fun hashCode(): Int = dimensions.hashCode()

    /** Rendered as `k=v,k=v` — this is what appears in cardinality-overflow warnings. */
    override fun toString(): String = dimensions.joinToString(",") { "${it.name}=${it.value}" }

    companion object {
        /**
         * The single bucket that every measurement lands in once the per-instrument cardinality cap
         * saturates.
         *
         * A visible `overflow=true` dimension rather than an empty key, so that the condition is
         * self-evident on the backend's console. Someone looking at a chart with a mysterious extra
         * series can search the dimension name and find this behaviour; a silently merged series
         * would just look like wrong data.
         */
        val Overflow: DimensionKey = DimensionKey(listOf(Dimension("overflow", "true")))
    }
}

/**
 * Build a [DimensionKey] from [attributes], keeping only names present in [allowed].
 *
 * Called on the hot path for every measurement, so it avoids allocation when it can: an empty
 * attribute set or an empty allowlist short-circuits to a dimensionless key.
 *
 * @param attributes the attributes supplied at the call site.
 * @param allowed dimension names to retain. Everything else is discarded, not merged — two calls
 *   differing only in a non-allowlisted attribute intentionally aggregate together.
 * @return a key whose dimensions are sorted by name.
 */
internal fun dimensionsOf(attributes: Attributes, allowed: Set<String>): DimensionKey {
    if (attributes.isEmpty || allowed.isEmpty()) return DimensionKey(emptyList())

    val dims = attributes.keys
        .filter { it.name in allowed }
        .mapNotNull { key ->
            // `Attributes.getOrNull` is typed `<T : Any>(AttributeKey<T>)`, but iterating `keys`
            // yields `AttributeKey<*>` with the type argument erased. The cast is unavoidable and
            // safe: we immediately call toString() and never treat the value as its static type.
            @Suppress("UNCHECKED_CAST")
            val value = attributes.getOrNull(key as AttributeKey<Any>) ?: return@mapNotNull null

            // toString() rather than a typed conversion: dimension values are strings, and the SDK's
            // dimension-worthy attributes (service name, operation name) already are strings. A
            // non-string attribute renders however its type renders, which is the best available
            // behaviour without a per-type registry.
            Dimension(key.name, value.toString())
        }
        // Sorted so that logically equal attribute sets always produce equal keys. `Attributes`
        // makes no ordering guarantee, so without this the same series could split in two depending
        // on the order attributes happened to be populated in.
        .sortedBy { it.name }

    return DimensionKey(dims)
}

/**
 * Bounds the number of distinct attribute sets tracked for a single instrument.
 *
 * ### Why this is not optional
 *
 * Some SDK metric attributes are influenced by the environment or by remote responses — error codes
 * and endpoint values in particular. An unguarded registry therefore has no upper bound on either
 * heap or backend spend, and both are driven by input the application does not control. A
 * misbehaving upstream returning many distinct error codes would be enough. OpenTelemetry's metrics
 * SDK applies the same mitigation for the same reason.
 *
 * ### Why overflow rather than drop
 *
 * Once saturated, new attribute sets collapse into [DimensionKey.Overflow] instead of being
 * discarded. Aggregate totals therefore stay correct — only the breakdown is lost. Dropping would
 * silently understate traffic, which is a worse failure for a metric whose usual purpose is spotting
 * a spike.
 *
 * Note this bounds the number of attribute *sets*. It does **not** bound the number of distinct
 * *values within* one set; that is a separate axis handled by [DistributionAggregator]'s own limit.
 *
 * Not thread-safe on its own: callers hold the owning instrument's lock.
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
        // Checked first: an already-admitted key must never be re-counted against the cap,
        // otherwise a steady workload would saturate the guard over time.
        key in seen -> key
        seen.size < max -> key.also { seen.add(it) }
        else -> DimensionKey.Overflow
    }

    /**
     * Forget all admitted keys.
     *
     * Deliberately **not** called at the end of each collection cycle. Resetting per cycle would let
     * a high-cardinality workload re-admit a fresh `max` attribute sets every interval, turning the
     * cap into a per-interval allowance and defeating the cost control. The guard is intended to be
     * sticky for the process lifetime; this exists for tests and for a future explicit-reset API.
     */
    fun reset(): Unit = seen.clear()
}
