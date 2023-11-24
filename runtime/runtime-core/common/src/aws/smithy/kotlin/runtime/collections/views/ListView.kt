/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class ListView<Src, Dest>(
    private val src: List<Src>,
    private val src2Dest: (Src) -> Dest,
    private val dest2Src: (Dest) -> Src,
) : List<Dest>, CollectionView<Src, Dest>(src, src2Dest, dest2Src) {
    override fun get(index: Int): Dest = src2Dest(src[index])

    override fun indexOf(element: Dest): Int = src.indexOf(dest2Src(element))

    override fun lastIndexOf(element: Dest): Int = src.lastIndexOf(dest2Src(element))

    override fun listIterator(): ListIterator<Dest> = src.listIterator().asView(src2Dest)

    override fun listIterator(index: Int): ListIterator<Dest> = src.listIterator(index).asView(src2Dest)

    override fun subList(fromIndex: Int, toIndex: Int): List<Dest> =
        src.subList(fromIndex, toIndex).asView(src2Dest, dest2Src)
}
