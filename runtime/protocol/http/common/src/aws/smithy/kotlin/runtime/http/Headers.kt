/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import aws.smithy.kotlin.runtime.collections.ValuesMap
import aws.smithy.kotlin.runtime.collections.ValuesMapBuilder
import aws.smithy.kotlin.runtime.collections.ValuesMapImpl
import aws.smithy.kotlin.runtime.collections.deepCopy
import aws.smithy.kotlin.runtime.util.CanDeepCopy

/**
 * Immutable mapping of case insensitive HTTP header names to list of [String] values.
 */
public interface Headers : ValuesMap<String> {
    public companion object {
        public operator fun invoke(block: HeadersBuilder.() -> Unit): Headers = HeadersBuilder()
            .apply(block).build()

        /**
         * Empty [Headers] instance
         */
        public val Empty: Headers = EmptyHeaders
    }
}

private object EmptyHeaders : Headers {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String> = emptyList()
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<String>>> = emptySet()
    override fun contains(name: String): Boolean = false
    override fun isEmpty(): Boolean = true
}

/**
 * Build an immutable HTTP header map
 */
public class HeadersBuilder : ValuesMapBuilder<String>(true, 8), CanDeepCopy<HeadersBuilder> {
    override fun toString(): String = "HeadersBuilder ${entries()} "
    override fun build(): Headers = HeadersImpl(values)

    override fun deepCopy(): HeadersBuilder {
        val originalValues = values.deepCopy()
        return HeadersBuilder().apply { values.putAll(originalValues) }
    }
}

private class HeadersImpl(
    values: Map<String, List<String>>,
) : Headers, ValuesMapImpl<String>(true, values) {
    override fun toString(): String = "Headers ${entries()}"
}
