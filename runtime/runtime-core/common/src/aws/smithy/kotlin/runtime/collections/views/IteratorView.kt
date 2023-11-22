/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class IteratorView<Src, Dest>(
    private val src: Iterator<Src>,
    private val src2Dest: (Src) -> Dest,
) : Iterator<Dest> {
    override fun hasNext(): Boolean = src.hasNext()

    override fun next(): Dest = src2Dest(src.next())
}
