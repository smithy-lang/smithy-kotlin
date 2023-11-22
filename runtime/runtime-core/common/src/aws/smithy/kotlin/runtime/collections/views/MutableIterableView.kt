/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal class MutableIterableView<Src, Dest>(
    private val src: MutableIterable<Src>,
    private val src2Dest: (Src) -> Dest,
) : MutableIterable<Dest>, IterableView<Src, Dest>(src, src2Dest) {
    override fun iterator(): MutableIterator<Dest> = src.iterator().asView(src2Dest)
}
