/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.util.*
import kotlinx.coroutines.Deferred

/**
 * Immutable mapping of case insensitive HTTP header names to list of [String] values.
 */
public interface DeferredHeaders : DeferredValuesMap<String> {
    public companion object {
        public operator fun invoke(block: DeferredHeadersBuilder.() -> Unit): DeferredHeaders = DeferredHeadersBuilder()
            .apply(block).build()

        /**
         * Empty [Headers] instance
         */
        public val Empty: DeferredHeaders = EmptyDeferredHeaders

        public suspend fun DeferredHeaders.toHeaders(): Headers = when (this) {
            is EmptyDeferredHeaders -> Headers.Empty
            else -> {
                HeadersBuilder().apply {
                    this@toHeaders.entries().forEach { (headerName, deferredValues) ->
                        deferredValues.forEach { deferredValue ->
                            append(headerName, deferredValue.await())
                        }
                    }
                }.build()
            }
        }
    }
}

private object EmptyDeferredHeaders : DeferredHeaders {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<Deferred<String>> = emptyList()
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<Deferred<String>>>> = emptySet()
    override fun contains(name: String): Boolean = false
    override fun isEmpty(): Boolean = true
}

/**
 * Perform a deep copy of this map, specifically duplicating the value lists so that they're insulated from changes.
 * @return A new map instance with copied value lists.
 */
internal fun Map<String, List<Deferred<String>>>.deepCopy() = mapValues { (_, v) -> v.toMutableList() }

/**
 * Build an immutable HTTP header map
 */
public class DeferredHeadersBuilder : DeferredValuesMapBuilder<String>(true, 8), CanDeepCopy<DeferredHeadersBuilder> {
    override fun toString(): String = "DeferredHeadersBuilder ${entries()} "
    override fun build(): DeferredHeaders = DeferredHeadersImpl(values)

    override fun deepCopy(): DeferredHeadersBuilder {
        val originalValues = values.deepCopy()
        return DeferredHeadersBuilder().apply { values.putAll(originalValues) }
    }
}

private class DeferredHeadersImpl(
    values: Map<String, List<Deferred<String>>>,
) : DeferredHeaders, DeferredValuesMapImpl<String>(true, values) {
    override fun toString(): String = "Headers ${entries()}"
}
