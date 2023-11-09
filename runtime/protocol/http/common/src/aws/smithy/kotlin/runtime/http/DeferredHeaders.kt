/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.ValuesMap
import aws.smithy.kotlin.runtime.collections.ValuesMapBuilder
import aws.smithy.kotlin.runtime.collections.ValuesMapImpl
import aws.smithy.kotlin.runtime.collections.deepCopy
import aws.smithy.kotlin.runtime.util.CanDeepCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * Immutable mapping of case insensitive HTTP header names to list of [Deferred] [String] values.
 */
public interface DeferredHeaders : ValuesMap<Deferred<String>> {
    public companion object {
        public operator fun invoke(block: DeferredHeadersBuilder.() -> Unit): DeferredHeaders = DeferredHeadersBuilder()
            .apply(block).build()

        /**
         * Empty [DeferredHeaders] instance
         */
        public val Empty: DeferredHeaders = EmptyDeferredHeaders
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
 * Build an immutable HTTP deferred header map
 */
public class DeferredHeadersBuilder : ValuesMapBuilder<Deferred<String>>(true, 8), CanDeepCopy<DeferredHeadersBuilder> {
    override fun build(): DeferredHeaders = DeferredHeadersImpl(values)
    override fun deepCopy(): DeferredHeadersBuilder {
        val originalValues = values.deepCopy()
        return DeferredHeadersBuilder().apply { values.putAll(originalValues) }
    }
    public fun add(name: String, value: String): Unit = append(name, CompletableDeferred(value))
}

private class DeferredHeadersImpl(
    values: Map<String, List<Deferred<String>>>,
) : DeferredHeaders, ValuesMapImpl<Deferred<String>>(true, values)

/**
 * Convert a [DeferredHeaders] instance to [Headers]. This will block while awaiting all [Deferred] header values.
 */
@InternalApi
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
