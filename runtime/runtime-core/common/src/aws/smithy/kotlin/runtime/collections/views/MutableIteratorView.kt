/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal class MutableIteratorView<Src, Dest>(
    private val src: MutableIterator<Src>,
    src2Dest: (Src) -> Dest,
) : MutableIterator<Dest>, IteratorView<Src, Dest>(src, src2Dest) {
    override fun remove() {
        src.remove()
    }
}
