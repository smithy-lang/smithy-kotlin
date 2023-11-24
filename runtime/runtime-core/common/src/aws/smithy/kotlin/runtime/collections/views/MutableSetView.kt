/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class MutableSetView<Src, Dest>(
    private val src: MutableSet<Src>,
    private val src2Dest: (Src) -> Dest,
    private val dest2Src: (Dest) -> Src,
) : MutableSet<Dest>, SetView<Src, Dest>(src, src2Dest, dest2Src) {
    override fun add(element: Dest): Boolean = src.add(dest2Src(element))

    override fun addAll(elements: Collection<Dest>): Boolean = src.addAll(elements.asView(dest2Src, src2Dest))

    override fun clear() {
        src.clear()
    }

    override fun iterator(): MutableIterator<Dest> = src.iterator().asView(src2Dest)

    override fun retainAll(elements: Collection<Dest>): Boolean = src.retainAll(elements.asView(dest2Src, src2Dest))

    override fun removeAll(elements: Collection<Dest>): Boolean = src.removeAll(elements.asView(dest2Src, src2Dest))

    override fun remove(element: Dest): Boolean = src.remove(dest2Src(element))
}
