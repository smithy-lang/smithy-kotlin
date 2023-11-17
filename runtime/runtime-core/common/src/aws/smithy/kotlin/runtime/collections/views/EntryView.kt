/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class EntryView<KSrc, KDest, VSrc, VDest>(
    private val src: Map.Entry<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val vSrc2Dest: (VSrc) -> VDest,
) : Map.Entry<KDest, VDest> {
    override val key: KDest
        get() = kSrc2Dest(src.key)

    override val value: VDest
        get() = vSrc2Dest(src.value)
}
