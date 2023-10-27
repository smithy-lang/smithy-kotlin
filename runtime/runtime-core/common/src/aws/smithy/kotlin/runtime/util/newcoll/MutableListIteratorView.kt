/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util.newcoll

/**
 * A mutable view of a mutable list iterator. This class presents the elements of a source iterator in a different
 * format/type. Updates to this iterator (e.g., advancing to the next element, additions, removals, etc.) are propagated
 * to the source iterator.
 * @param Src The type of elements in the source iterator
 * @param Dest The type of elements in this view
 * @param srcIterator The source iterator containing the canonical elements
 * @param srcToDest A function that transforms a [Src] object to a [Dest]
 * @param destToSrc A function that transforms a [Dest] object to a [Src]
 */
public class MutableListIteratorView<Src, Dest>(
    private val srcIterator: MutableListIterator<Src>,
    private val srcToDest: (Src) -> Dest,
    private val destToSrc: (Dest) -> Src,
) : MutableListIterator<Dest> {
    override fun add(element: Dest) {
        srcIterator.add(destToSrc(element))
    }

    override fun hasNext(): Boolean = srcIterator.hasNext()

    override fun hasPrevious(): Boolean = srcIterator.hasPrevious()

    override fun next(): Dest = srcToDest(srcIterator.next())

    override fun nextIndex(): Int = srcIterator.nextIndex()

    override fun previous(): Dest = srcToDest(srcIterator.previous())

    override fun previousIndex(): Int = srcIterator.previousIndex()

    override fun remove() {
        srcIterator.remove()
    }

    override fun set(element: Dest) {
        srcIterator.set(destToSrc(element))
    }
}
