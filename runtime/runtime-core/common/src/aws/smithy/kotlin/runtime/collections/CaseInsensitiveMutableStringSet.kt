/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.collections

internal class CaseInsensitiveMutableStringSet(
    initialValues: Iterable<CaseInsensitiveString> = setOf(),
) : MutableSet<String> {
    private val delegate = initialValues.toMutableSet()

    override fun add(element: String) = delegate.add(element.toInsensitive())
    override fun clear() = delegate.clear()
    override fun contains(element: String) = delegate.contains(element.toInsensitive())
    override fun containsAll(elements: Collection<String>) = elements.all { it in this }
    override fun equals(other: Any?) = other is CaseInsensitiveMutableStringSet && delegate == other.delegate
    override fun hashCode() = delegate.hashCode()
    override fun isEmpty() = delegate.isEmpty()
    override fun remove(element: String) = delegate.remove(element.toInsensitive())
    override val size: Int get() = delegate.size
    override fun toString() = delegate.toString()

    override fun addAll(elements: Collection<String>) =
        elements.fold(false) { modified, item -> add(item) || modified }

    override fun iterator() = object : MutableIterator<String> {
        val delegate = this@CaseInsensitiveMutableStringSet.delegate.iterator()
        override fun hasNext() = delegate.hasNext()
        override fun next() = delegate.next().normalized
        override fun remove() = delegate.remove()
    }

    override fun removeAll(elements: Collection<String>) =
        elements.fold(false) { modified, item -> remove(item) || modified }

    override fun retainAll(elements: Collection<String>): Boolean {
        val insensitiveElements = elements.map { it.toInsensitive() }.toSet()
        val toRemove = delegate.filterNot { it in insensitiveElements }
        return toRemove.fold(false) { modified, item -> delegate.remove(item) || modified }
    }
}

internal fun CaseInsensitiveMutableStringSet(initialValues: Iterable<String>) =
    CaseInsensitiveMutableStringSet(initialValues.map { it.toInsensitive() })
