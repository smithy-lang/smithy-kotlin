/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import io.ktor.utils.io.bits.*
import java.nio.ByteBuffer

internal actual object DefaultAllocator : Allocator {
    override fun alloc(size: ULong): Memory {
        require(size <= Int.MAX_VALUE.toULong()) { "Unable to allocate $size bytes" }
        return Memory.of(ByteBuffer.allocate(size.toInt()))
    }
    override fun free(instance: Memory) {}
}
