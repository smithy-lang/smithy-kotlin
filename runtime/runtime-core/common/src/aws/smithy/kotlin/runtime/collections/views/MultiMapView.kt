/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

import aws.smithy.kotlin.runtime.collections.Entry
import aws.smithy.kotlin.runtime.collections.MultiMap

internal class MultiMapView<KSrc, KDest, VSrc, VDest>(
    private val src: MultiMap<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val kDest2Src: (KDest) -> KSrc,
    private val vSrc2Dest: (VSrc) -> VDest,
    private val vDest2Src: (VDest) -> VSrc,
) : MultiMap<KDest, VDest> {
    private val vListSrc2Dest: (List<VSrc>) -> List<VDest> = { it.asView(vSrc2Dest, vDest2Src) }
    private val vListDest2Src: (List<VDest>) -> List<VSrc> = { it.asView(vDest2Src, vSrc2Dest) }

    private fun fwdEntryView(src: Map.Entry<KSrc, List<VSrc>>) = EntryView(src, kSrc2Dest, vListSrc2Dest)
    private fun revEntryView(dest: Map.Entry<KDest, List<VDest>>) = EntryView(dest, kDest2Src, vListDest2Src)

    override fun containsKey(key: KDest): Boolean = src.containsKey(kDest2Src(key))

    override fun containsValue(value: List<VDest>): Boolean = src.containsValue(vListDest2Src(value))

    override val entries: Set<Map.Entry<KDest, List<VDest>>>
        get() = src.entries.asView(::fwdEntryView, ::revEntryView)

    override fun get(key: KDest): List<VDest>? = src[kDest2Src(key)]?.let(vListSrc2Dest)

    override fun isEmpty(): Boolean = src.isEmpty()

    override val entryValues: Sequence<Map.Entry<KDest, VDest>> =
        src.entryValues.map { (kSrc, vSrc) -> Entry(kSrc2Dest(kSrc), vSrc2Dest(vSrc)) }

    override val keys: Set<KDest>
        get() = src.keys.asView(kSrc2Dest, kDest2Src)

    override val size: Int
        get() = src.size

    override val values: Collection<List<VDest>>
        get() = src.values.asView(vListSrc2Dest, vListDest2Src)
}
