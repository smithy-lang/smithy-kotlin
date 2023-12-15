/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class SetView<Src, Dest>(
    src: Set<Src>,
    src2Dest: (Src) -> Dest,
    dest2Src: (Dest) -> Src,
) : Set<Dest>, CollectionView<Src, Dest>(src, src2Dest, dest2Src)
