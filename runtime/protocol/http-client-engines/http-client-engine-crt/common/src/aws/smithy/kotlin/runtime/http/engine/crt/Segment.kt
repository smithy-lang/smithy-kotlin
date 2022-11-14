/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.smithy.kotlin.runtime.io.SdkBuffer

internal typealias Segment = SdkBuffer

/**
 * Create a segment from the given src [ByteArray] and mark the entire contents readable
 */
internal fun newReadableSegment(src: ByteArray): Segment = SdkBuffer().apply { write(src) }

internal fun Segment.copyTo(dest: SdkBuffer, limit: Int = Int.MAX_VALUE): Int {
    check(size > 0L) { "nothing left to read from segment" }
    val wlimit = minOf(size, limit.toLong())
    val wc = read(dest, wlimit)
    return wc.toInt()
}

internal fun Segment.copyTo(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): Int {
    check(size > 0L) { "nothing left to read from segment" }
    val wlimit = minOf(length.toLong(), size).toInt()
    val wc = read(dest, offset, wlimit)
    return wc
}
