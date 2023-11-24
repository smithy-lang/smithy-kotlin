/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class ListIteratorView<Src, Dest>(
    private val src: ListIterator<Src>,
    private val src2Dest: (Src) -> Dest,
) : ListIterator<Dest>, IteratorView<Src, Dest>(src, src2Dest) {
    override fun hasPrevious(): Boolean = src.hasPrevious()

    override fun nextIndex(): Int = src.nextIndex()

    override fun previous(): Dest = src2Dest(src.previous())

    override fun previousIndex(): Int = src.previousIndex()
}
