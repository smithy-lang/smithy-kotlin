/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.http.util.*
import aws.smithy.kotlin.runtime.util.LazyAsyncValue

/**
 * Immutable mapping of case insensitive HTTP header names to list of [String] values.
 */
public interface LazyHeaders : LazyAsyncValuesMap<String> {
    public companion object {
        public operator fun invoke(block: LazyHeadersBuilder.() -> Unit): LazyHeaders = LazyHeadersBuilder()
            .apply(block).build()

        /**
         * Empty [Headers] instance
         */
        public val Empty: LazyHeaders = EmptyLazyHeaders

        public suspend fun LazyHeaders.toHeaders(): Headers = when (this) {
            is EmptyLazyHeaders -> Headers.Empty
            else -> {
                HeadersBuilder().apply {
                    this@toHeaders.entries().forEach { (headerName, lazyValues) ->
                        lazyValues.forEach { lazyValue ->
                            append(headerName, lazyValue.get())
                        }
                    }
                }.build()
            }
        }
    }
}

private object EmptyLazyHeaders : LazyHeaders {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<LazyAsyncValue<String>> = emptyList()
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<LazyAsyncValue<String>>>> = emptySet()
    override fun contains(name: String): Boolean = false
    override fun isEmpty(): Boolean = true
}

/**
 * Perform a deep copy of this map, specifically duplicating the value lists so that they're insulated from changes.
 * @return A new map instance with copied value lists.
 */
internal fun Map<String, List<LazyAsyncValue<String>>>.deepCopy() = mapValues { (_, v) -> v.toMutableList() }

/**
 * Build an immutable HTTP header map
 */
public class LazyHeadersBuilder : LazyAsyncValuesMapBuilder<String>(true, 8), CanDeepCopy<LazyHeadersBuilder> {
    override fun toString(): String = "LazyHeadersBuilder ${entries()} "
    override fun build(): LazyHeaders = LazyHeadersImpl(values)

    override fun deepCopy(): LazyHeadersBuilder {
        val originalValues = values.deepCopy()
        return LazyHeadersBuilder().apply { values.putAll(originalValues) }
    }
}

private class LazyHeadersImpl(
    values: Map<String, List<LazyAsyncValue<String>>>,
) : LazyHeaders, LazyAsyncValuesMapImpl<String>(true, values) {
    override fun toString(): String = "Headers ${entries()}"
}
