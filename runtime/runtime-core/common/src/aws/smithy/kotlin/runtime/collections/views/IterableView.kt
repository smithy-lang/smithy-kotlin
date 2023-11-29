/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class IterableView<Src, Dest>(
    private val src: Iterable<Src>,
    private val src2Dest: (Src) -> Dest,
) : Iterable<Dest> {
    override fun iterator(): Iterator<Dest> = src.iterator().asView(src2Dest)
}
