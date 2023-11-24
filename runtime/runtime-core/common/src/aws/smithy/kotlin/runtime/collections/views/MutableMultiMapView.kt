/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

import aws.smithy.kotlin.runtime.collections.Entry
import aws.smithy.kotlin.runtime.collections.MutableMultiMap
import kotlin.jvm.JvmName

// FIXME As written, `getOrPut` doesn't work correctly on views because it can return a reference to the `defaultValue`
//  but that's ignored by views because we need to create the "canonical" data source in the `src` map.
internal class MutableMultiMapView<KSrc, KDest, VSrc, VDest>(
    private val src: MutableMultiMap<KSrc, VSrc>,
    private val kSrc2Dest: (KSrc) -> KDest,
    private val kDest2Src: (KDest) -> KSrc,
    private val vSrc2Dest: (VSrc) -> VDest,
    private val vDest2Src: (VDest) -> VSrc,
) : MutableMultiMap<KDest, VDest> {
    private val vListSrc2Dest: (MutableList<VSrc>) -> MutableList<VDest> = { it.asView(vSrc2Dest, vDest2Src) }

    private val vListDest2Src: (MutableList<VDest>) -> MutableList<VSrc> = {
        // FIXME this is not ideal because it forgets any connection to the given `MutableList<VDest>`
        it.mapTo(mutableListOf(), vDest2Src)
    }

    private fun ensureKey(key: KDest): MutableList<VDest> {
        // FIXME Can't use getOrPut(key, mutableListOf()) because that will return a reference to the `mutableListOf()`
        //  which is disconnected from any views. Values added would disappear into the aether.

        val existingList = get(key)
        return if (existingList == null) {
            src[kDest2Src(key)] = mutableListOf()
            getValue(key)
        } else {
            existingList
        }
    }

    private fun fwdEntryView(src: MutableMap.MutableEntry<KSrc, MutableList<VSrc>>) =
        MutableEntryView(src, kSrc2Dest, vListSrc2Dest, vListDest2Src)

    private fun revEntryView(dest: MutableMap.MutableEntry<KDest, MutableList<VDest>>) =
        MutableEntryView(dest, kDest2Src, vListDest2Src, vListSrc2Dest)

    override fun add(key: KDest, value: VDest): Boolean = ensureKey(key).add(value)

    override fun add(key: KDest, index: Int, value: VDest) {
        ensureKey(key).add(index, value)
    }

    override fun addAll(key: KDest, values: Collection<VDest>): Boolean = ensureKey(key).addAll(values)

    override fun addAll(key: KDest, index: Int, values: Collection<VDest>): Boolean =
        ensureKey(key).addAll(index, values)

    override fun clear() {
        src.clear()
    }

    override fun containsKey(key: KDest): Boolean = src.containsKey(kDest2Src(key))

    override fun containsValue(value: MutableList<VDest>): Boolean = src.containsValue(vListDest2Src(value))

    override val entries: MutableSet<MutableMap.MutableEntry<KDest, MutableList<VDest>>>
        get() = src.entries.asView(::fwdEntryView, ::revEntryView)

    override val entryValues: Sequence<Map.Entry<KDest, VDest>>
        get() = src.entryValues.map { (k, v) -> Entry(kSrc2Dest(k), vSrc2Dest(v)) }

    override fun get(key: KDest): MutableList<VDest>? = src[kDest2Src(key)]?.let(vListSrc2Dest)

    override fun isEmpty(): Boolean = src.isEmpty()

    override val keys: MutableSet<KDest>
        get() = src.keys.asView(kSrc2Dest, kDest2Src)

    override fun put(key: KDest, value: MutableList<VDest>): MutableList<VDest>? =
        src.put(kDest2Src(key), vListDest2Src(value))?.let(vListSrc2Dest)

    override fun putAll(from: Map<out KDest, MutableList<VDest>>) {
        from.entries.forEach { (kDest, vDest) ->
            src[kDest2Src(kDest)] = vListDest2Src(vDest)
        }

        // A naive approach of src.putAll(from.asView(kDest2Src, kSrc2Dest, vDest2Src, vSrc2Dest)) doesn't work because
        // [from] has a covariant key parameter and the compiler can't handle that with [kSrc2Dest]. Exact error is:
        // > Type mismatch: inferred type is KDest but CapturedType(out KDest) was expected
    }
    override fun remove(key: KDest): MutableList<VDest>? = src.remove(kDest2Src(key))?.let(vListSrc2Dest)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeElement")
    override fun remove(key: KDest, value: VDest): Boolean = src.remove(kDest2Src(key), vDest2Src(value))

    override fun removeAt(key: KDest, index: Int): VDest? = src.removeAt(kDest2Src(key), index)?.let(vSrc2Dest)

    override fun removeAll(key: KDest, values: Collection<VDest>): Boolean? =
        src.removeAll(kDest2Src(key), values.asView(vDest2Src, vSrc2Dest))

    override fun retainAll(key: KDest, values: Collection<VDest>): Boolean? =
        src[kDest2Src(key)]?.retainAll(values.asView(vDest2Src, vSrc2Dest))

    override val size: Int
        get() = src.size

    override val values: MutableCollection<MutableList<VDest>>
        get() = src.values.asView(vListSrc2Dest, vListDest2Src)
}
