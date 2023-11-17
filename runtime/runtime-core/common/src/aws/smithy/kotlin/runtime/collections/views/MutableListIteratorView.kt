/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class MutableListIteratorView<Src, Dest>(
    private val src: MutableListIterator<Src>,
    src2Dest: (Src) -> Dest,
    private val dest2Src: (Dest) -> Src,
) : MutableListIterator<Dest>, ListIteratorView<Src, Dest>(src, src2Dest) {
    override fun add(element: Dest) {
        src.add(dest2Src(element))
    }

    override fun remove() {
        src.remove()
    }

    override fun set(element: Dest) {
        src.set(dest2Src(element))
    }
}
