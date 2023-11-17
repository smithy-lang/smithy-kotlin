/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class MutableListView<Src, Dest>(
    private val src: MutableList<Src>,
    private val src2Dest: (Src) -> Dest,
    private val dest2Src: (Dest) -> Src,
) : MutableList<Dest>, ListView<Src, Dest>(src, src2Dest, dest2Src) {
    override fun add(element: Dest): Boolean = src.add(dest2Src(element))

    override fun add(index: Int, element: Dest) {
        src.add(index, dest2Src(element))
    }

    override fun addAll(index: Int, elements: Collection<Dest>): Boolean =
        src.addAll(index, elements.asView(dest2Src, src2Dest))

    override fun addAll(elements: Collection<Dest>): Boolean = src.addAll(elements.asView(dest2Src, src2Dest))

    override fun clear() {
        src.clear()
    }

    override fun iterator(): MutableIterator<Dest> = src.iterator().asView(src2Dest)

    override fun listIterator(): MutableListIterator<Dest> = src.listIterator().asView(src2Dest, dest2Src)

    override fun listIterator(index: Int): MutableListIterator<Dest> =
        src.listIterator(index).asView(src2Dest, dest2Src)

    override fun removeAt(index: Int): Dest = src2Dest(src.removeAt(index))

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Dest> =
        src.subList(fromIndex, toIndex).asView(src2Dest, dest2Src)

    override fun set(index: Int, element: Dest): Dest = src2Dest(src.set(index, dest2Src(element)))

    override fun retainAll(elements: Collection<Dest>): Boolean = src.retainAll(elements.asView(dest2Src, src2Dest))

    override fun removeAll(elements: Collection<Dest>): Boolean = src.removeAll(elements.asView(dest2Src, src2Dest))

    override fun remove(element: Dest): Boolean = src.remove(dest2Src(element))
}
