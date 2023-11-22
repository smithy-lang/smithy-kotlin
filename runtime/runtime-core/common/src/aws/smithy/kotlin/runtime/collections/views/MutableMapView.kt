/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

internal open class MutableMapView<KSrc, KDest, VSrc, VDest>(
    private val src: MutableMap<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val kDest2Src: (KDest) -> KSrc,
    private val vSrc2Dest: (VSrc) -> VDest,
    private val vDest2Src: (VDest) -> VSrc,
) : MutableMap<KDest, VDest>, MapView<KSrc, KDest, VSrc, VDest>(src, kSrc2Dest, kDest2Src, vSrc2Dest, vDest2Src) {
    private fun fwdEntryView(src: MutableMap.MutableEntry<KSrc, VSrc>) =
        MutableEntryView(src, kSrc2Dest, vSrc2Dest, vDest2Src)

    private fun revEntryView(dest: MutableMap.MutableEntry<KDest, VDest>) =
        MutableEntryView(dest, kDest2Src, vDest2Src, vSrc2Dest)

    override fun clear() {
        src.clear()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<KDest, VDest>>
        get() = src.entries.asView(::fwdEntryView, ::revEntryView)

    override val keys: MutableSet<KDest>
        get() = src.keys.asView(kSrc2Dest, kDest2Src)

    override fun put(key: KDest, value: VDest): VDest? = src.put(kDest2Src(key), vDest2Src(value))?.let(vSrc2Dest)

    override fun putAll(from: Map<out KDest, VDest>) {
        from.entries.forEach { (kDest, vDest) ->
            src[kDest2Src(kDest)] = vDest2Src(vDest)
        }

        // A naive approach of src.putAll(from.asView(kDest2Src, kSrc2Dest, vDest2Src, vSrc2Dest)) doesn't work because
        // [from] has a covariant key parameter and the compiler can't handle that with [kSrc2Dest]. Exact error is:
        // > Type mismatch: inferred type is KDest but CapturedType(out KDest) was expected
    }

    override fun remove(key: KDest): VDest? = src.remove(kDest2Src(key))?.let(vSrc2Dest)

    override val values: MutableCollection<VDest>
        get() = src.values.asView(vSrc2Dest, vDest2Src)
}
