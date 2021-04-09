// ktlint-disable filename
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

import io.ktor.utils.io.bits.*
import java.nio.ByteBuffer

internal actual object DefaultAllocator : Allocator {
    override fun alloc(size: Int): Memory = Memory.of(ByteBuffer.allocate(size))
    override fun free(instance: Memory) {}
}
