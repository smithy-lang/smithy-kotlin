/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

@OptIn(ExperimentalIoApi::class)
internal interface Allocator {
    fun alloc(size: ULong): Memory
    // FIXME - we should revisit this - Kotlin/Native is only place where we would actually be manually managing memory
    // and that story may change to the point where a free() function isn't even necessary
    fun free(instance: Memory)
}

// allocate using the most appropriate underlying platform type (e.g. ByteBuffer on JVM, ArrayBuffer on JS, etc)
internal expect object DefaultAllocator : Allocator

/**
 * Round up to the next power of 2. [size] should be non-negative
 */
internal fun ceilp2(size: ULong): ULong {
    var x = size - 1u
    x = x or (x shr 1)
    x = x or (x shr 2)
    x = x or (x shr 4)
    x = x or (x shr 8)
    x = x or (x shr 16)
    return x + 1u
}

/**
 * Allocate new memory of size [newSize], copy the contents of [instance] into it and free [instance]
 * and return the newly allocated memory.
 *
 * The memory of [instance] should no longer be used after calling.
 */
@OptIn(ExperimentalIoApi::class)
internal fun Allocator.realloc(instance: Memory, newSize: ULong): Memory {
    require(newSize >= instance.size.toULong())
    val newInstance = alloc(newSize)
    instance.copyTo(newInstance, 0, instance.size32, 0)
    free(instance)
    return newInstance
}
