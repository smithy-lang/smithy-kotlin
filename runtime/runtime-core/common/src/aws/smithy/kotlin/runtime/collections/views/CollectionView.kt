/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class CollectionView<Src, Dest>(
    private val src: Collection<Src>,
    private val src2Dest: (Src) -> Dest,
    private val dest2Src: (Dest) -> Src,
) : Collection<Dest>, IterableView<Src, Dest>(src, src2Dest) {
    override fun contains(element: Dest): Boolean = src.contains(dest2Src(element))

    override fun containsAll(elements: Collection<Dest>): Boolean = src.containsAll(elements.asView(dest2Src, src2Dest))

    override fun isEmpty(): Boolean = src.isEmpty()

    override val size: Int
        get() = src.size
}
