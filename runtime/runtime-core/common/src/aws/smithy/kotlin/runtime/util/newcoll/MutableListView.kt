/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util.newcoll

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A mutable view of a mutable list. This class presents the elements of a source list in a different format/type.
 * Updates to this view (e.g., additions, removals, etc.) are propagated to the source list.
 * @param Src The type of elements in the source list
 * @param Dest The type of elements in this view
 * @param srcList The source list containing the canonical elements
 * @param srcToDest A function that transforms a [Src] object to a [Dest]
 * @param destToSrc A function that transforms a [Dest] object to a [Src]
 */
@InternalApi
public class MutableListView<Src, Dest>(
    private val srcList: MutableList<Src>,
    private val srcToDest: (Src) -> Dest,
    private val destToSrc: (Dest) -> Src,
) : MutableList<Dest> {
    override fun add(element: Dest): Boolean = srcList.add(destToSrc(element))

    override fun add(index: Int, element: Dest) {
        srcList.add(index, destToSrc(element))
    }

    override fun addAll(elements: Collection<Dest>): Boolean = srcList.addAll(elements.map(destToSrc))

    override fun addAll(index: Int, elements: Collection<Dest>): Boolean =
        srcList.addAll(index, elements.map(destToSrc))

    override fun clear() {
        srcList.clear()
    }

    override fun contains(element: Dest): Boolean = srcList.contains(destToSrc(element))

    override fun containsAll(elements: Collection<Dest>): Boolean = srcList.containsAll(elements.map(destToSrc))

    override fun get(index: Int): Dest = srcToDest(srcList[index])

    override fun indexOf(element: Dest): Int = srcList.indexOf(destToSrc(element))

    override fun isEmpty(): Boolean = srcList.isEmpty()

    override fun iterator(): MutableIterator<Dest> = MutableIteratorView(srcList.iterator(), srcToDest)

    override fun lastIndexOf(element: Dest): Int = srcList.lastIndexOf(destToSrc(element))

    override fun listIterator(): MutableListIterator<Dest> =
        MutableListIteratorView(srcList.listIterator(), srcToDest, destToSrc)

    override fun listIterator(index: Int): MutableListIterator<Dest> =
        MutableListIteratorView(srcList.listIterator(index), srcToDest, destToSrc)

    override fun remove(element: Dest): Boolean = srcList.remove(destToSrc(element))

    override fun removeAll(elements: Collection<Dest>): Boolean = srcList.removeAll(elements.map(destToSrc))

    override fun removeAt(index: Int): Dest = srcToDest(srcList.removeAt(index))

    override fun retainAll(elements: Collection<Dest>): Boolean = srcList.retainAll(elements.map(destToSrc))

    override fun set(index: Int, element: Dest): Dest = srcToDest(srcList.set(index, destToSrc(element)))

    override val size: Int
        get() = srcList.size

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Dest> = MutableListView(
        srcList.subList(fromIndex, toIndex),
        srcToDest,
        destToSrc,
    )
}
