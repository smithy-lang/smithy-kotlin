/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class MapView<KSrc, KDest, VSrc, VDest>(
    private val src: Map<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val kDest2Src: (KDest) -> KSrc,
    private val vSrc2Dest: (VSrc) -> VDest,
    private val vDest2Src: (VDest) -> VSrc,
) : Map<KDest, VDest> {
    private fun fwdEntryView(src: Map.Entry<KSrc, VSrc>) = EntryView(src, kSrc2Dest, vSrc2Dest)
    private fun revEntryView(dest: Map.Entry<KDest, VDest>) = EntryView(dest, kDest2Src, vDest2Src)

    override fun containsKey(key: KDest): Boolean = src.containsKey(kDest2Src(key))

    override fun containsValue(value: VDest): Boolean = src.containsValue(vDest2Src(value))

    override val entries: Set<Map.Entry<KDest, VDest>>
        get() = src.entries.asView(::fwdEntryView, ::revEntryView)

    override fun get(key: KDest): VDest? = src[kDest2Src(key)]?.let(vSrc2Dest)

    override fun isEmpty(): Boolean = src.isEmpty()

    override val keys: Set<KDest>
        get() = src.keys.asView(kSrc2Dest, kDest2Src)

    override val size: Int
        get() = src.size

    override val values: Collection<VDest>
        get() = src.values.asView(vSrc2Dest, vDest2Src)
}
