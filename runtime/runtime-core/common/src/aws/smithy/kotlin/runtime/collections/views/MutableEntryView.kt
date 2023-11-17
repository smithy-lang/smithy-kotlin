/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal class MutableEntryView<KSrc, KDest, VSrc, VDest>(
    private val src: MutableMap.MutableEntry<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val vSrc2Dest: (VSrc) -> VDest,
    private val vDest2Src: (VDest) -> VSrc,
) : MutableMap.MutableEntry<KDest, VDest>, EntryView<KSrc, KDest, VSrc, VDest>(src, kSrc2Dest, vSrc2Dest) {
    override fun setValue(newValue: VDest): VDest = vSrc2Dest(src.setValue(vDest2Src(newValue)))
}
