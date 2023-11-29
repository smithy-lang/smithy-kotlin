/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections.views

import aws.smithy.kotlin.runtime.collections.MultiMap
import aws.smithy.kotlin.runtime.collections.MutableMultiMap

internal fun <Src, Dest> Collection<Src>.asView(src2Dest: (Src) -> Dest, dest2Src: (Dest) -> Src): Collection<Dest> =
    CollectionView(this, src2Dest, dest2Src)

internal fun <Src, Dest> Iterable<Src>.asView(src2Dest: (Src) -> Dest): IterableView<Src, Dest> =
    IterableView(this, src2Dest)

internal fun <Src, Dest> Iterator<Src>.asView(src2Dest: (Src) -> Dest): IteratorView<Src, Dest> =
    IteratorView(this, src2Dest)

internal fun <Src, Dest> List<Src>.asView(src2Dest: (Src) -> Dest, dest2Src: (Dest) -> Src): ListView<Src, Dest> =
    ListView(this, src2Dest, dest2Src)

internal fun <Src, Dest> ListIterator<Src>.asView(src2Dest: (Src) -> Dest): ListIteratorView<Src, Dest> =
    ListIteratorView(this, src2Dest)

internal fun <KSrc, KDest, VSrc, VDest> Map<KSrc, VSrc>.asView(
    kSrc2Dest: (KSrc) -> KDest,
    kDest2Src: (KDest) -> KSrc,
    vSrc2Dest: (VSrc) -> VDest,
    vDest2Src: (VDest) -> VSrc,
) = MapView(this, kSrc2Dest, kDest2Src, vSrc2Dest, vDest2Src)

internal fun <KSrc, KDest, VSrc, VDest> MultiMap<KSrc, VSrc>.asView(
    kSrc2Dest: (KSrc) -> KDest,
    kDest2Src: (KDest) -> KSrc,
    vSrc2Dest: (VSrc) -> VDest,
    vDest2Src: (VDest) -> VSrc,
) = MultiMapView(this, kSrc2Dest, kDest2Src, vSrc2Dest, vDest2Src)

internal fun <Src, Dest> MutableCollection<Src>.asView(
    src2Dest: (Src) -> Dest,
    dest2Src: (Dest) -> Src,
): MutableCollectionView<Src, Dest> = MutableCollectionView(this, src2Dest, dest2Src)

internal fun <Src, Dest> MutableIterable<Src>.asView(src2Dest: (Src) -> Dest): MutableIterableView<Src, Dest> =
    MutableIterableView(this, src2Dest)

internal fun <Src, Dest> MutableIterator<Src>.asView(src2Dest: (Src) -> Dest): MutableIteratorView<Src, Dest> =
    MutableIteratorView(this, src2Dest)

internal fun <Src, Dest> MutableList<Src>.asView(
    src2Dest: (Src) -> Dest,
    dest2Src: (Dest) -> Src,
): MutableListView<Src, Dest> = MutableListView(this, src2Dest, dest2Src)

internal fun <Src, Dest> MutableListIterator<Src>.asView(
    src2Dest: (Src) -> Dest,
    dest2Src: (Dest) -> Src,
): MutableListIteratorView<Src, Dest> = MutableListIteratorView(this, src2Dest, dest2Src)

internal fun <KSrc, KDest, VSrc, VDest> MutableMap<KSrc, VSrc>.asView(
    kSrc2Dest: (KSrc) -> KDest,
    kDest2Src: (KDest) -> KSrc,
    vSrc2Dest: (VSrc) -> VDest,
    vDest2Src: (VDest) -> VSrc,
) = MutableMapView(this, kSrc2Dest, kDest2Src, vSrc2Dest, vDest2Src)

internal fun <KSrc, KDest, VSrc, VDest> MutableMultiMap<KSrc, VSrc>.asView(
    kSrc2Dest: (KSrc) -> KDest,
    kDest2Src: (KDest) -> KSrc,
    vSrc2Dest: (VSrc) -> VDest,
    vDest2Src: (VDest) -> VSrc,
) = MutableMultiMapView(this, kSrc2Dest, kDest2Src, vSrc2Dest, vDest2Src)

internal fun <Src, Dest> MutableSet<Src>.asView(
    src2Dest: (Src) -> Dest,
    dest2Src: (Dest) -> Src,
): MutableSetView<Src, Dest> = MutableSetView(this, src2Dest, dest2Src)

internal fun <Src, Dest> Set<Src>.asView(src2Dest: (Src) -> Dest, dest2Src: (Dest) -> Src): SetView<Src, Dest> =
    SetView(this, src2Dest, dest2Src)
