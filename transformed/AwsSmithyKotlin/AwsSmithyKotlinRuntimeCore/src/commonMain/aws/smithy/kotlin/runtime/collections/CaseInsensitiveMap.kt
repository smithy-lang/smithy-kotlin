/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Map of case-insensitive [String] to [Value]
 */
@InternalApi
internal class CaseInsensitiveMap<Value> : MutableMap<String, Value> {
    private val impl: MutableMap<CaseInsensitiveString, Value> = mutableMapOf()

    override val size: Int
        get() = impl.size

    override fun containsKey(key: String): Boolean = impl.containsKey(key.toInsensitive())

    override fun containsValue(value: Value): Boolean = impl.containsValue(value)

    override fun get(key: String): Value? = impl[key.toInsensitive()]

    override fun isEmpty(): Boolean = impl.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<String, Value>>
        get() = impl.entries.map {
            Entry(it.key.normalized, it.value)
        }.toMutableSet()

    override val keys: MutableSet<String>
        get() = CaseInsensitiveMutableStringSet(impl.keys)

    override val values: MutableCollection<Value>
        get() = impl.values

    override fun clear() = impl.clear()

    override fun put(key: String, value: Value): Value? = impl.put(key.toInsensitive(), value)

    override fun putAll(from: Map<out String, Value>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun remove(key: String): Value? = impl.remove(key.toInsensitive())

    override fun hashCode() = impl.hashCode()

    override fun equals(other: Any?) = other is CaseInsensitiveMap<*> && impl == other.impl

    override fun toString() = impl.toString()

    private class Entry<Key, Value>(
        override val key: Key,
        override var value: Value,
    ) : MutableMap.MutableEntry<Key, Value> {

        override fun setValue(newValue: Value): Value {
            value = newValue
            return value
        }

        override fun hashCode(): Int = key.hashCode() xor value.hashCode() // Match JVM & K/N stdlib implementations

        override fun equals(other: Any?): Boolean {
            if (other == null || other !is Map.Entry<*, *>) return false
            return other.key == key && other.value == value
        }

        override fun toString(): String = "$key=$value"
    }
}
