/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util.newcoll

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A mutable view of a mutable iterator. This class presents the elements of a source iterator in a different
 * format/type. Updates to this iterator (i.e., advancing to the next element or removing the current element) are
 * propagated to the source iterator.
 * @param Src The type of elements in the source iterator
 * @param Dest The type of elements in this view
 * @param srcIterator The source iterator containing the canonical elements
 * @param srcToDest A function that transforms a [Src] object to a [Dest]
 */
@InternalApi
public class MutableIteratorView<Src, Dest>(
    private val srcIterator: MutableIterator<Src>,
    private val srcToDest: (Src) -> Dest,
) : MutableIterator<Dest> {
    override fun hasNext(): Boolean = srcIterator.hasNext()

    override fun next(): Dest = srcToDest(srcIterator.next())

    override fun remove() {
        srcIterator.remove()
    }
}
