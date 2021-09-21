/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

/**
 * Read a single byte from the buffer
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readByte(): Byte {
    val value = memory.loadAt(readPosition)
    discard(1)
    return value
}

/**
 * Write a single byte to the buffer
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeByte(value: Byte) {
    memory.storeAt(writePosition, value)
    commitWritten(1)
}

/**
 * Read a signed 16-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readShort(): Short {
    val value = memory.loadShortAt(readPosition)
    discard(2)
    return value
}

/**
 * Write a signed 16-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeShort(value: Short) {
    memory.storeShortAt(writePosition, value)
    commitWritten(2)
}

/**
 * Read a signed 32-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readInt(): Int {
    val value = memory.loadIntAt(readPosition)
    discard(4)
    return value
}

/**
 * Write a signed 32-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeInt(value: Int) {
    memory.storeIntAt(writePosition, value)
    commitWritten(4)
}

/**
 * Read a signed 64-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readLong(): Long {
    val value = memory.loadLongAt(readPosition)
    discard(8)
    return value
}

/**
 * Write a signed 64-bit integer in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeLong(value: Long) {
    memory.storeLongAt(writePosition, value)
    commitWritten(8)
}

/**
 * Read a 32-bit float in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readFloat(): Float {
    val value = memory.loadFloatAt(readPosition)
    discard(4)
    return value
}

/**
 * Write a 32-bit float in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeFloat(value: Float) {
    memory.storeFloatAt(writePosition, value)
    commitWritten(4)
}

/**
 * Read a 64-bit double in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.readDouble(): Double {
    val value = memory.loadDoubleAt(readPosition)
    discard(8)
    return value
}
/**
 * Write a 32-bit float in big-endian byte order
 */
@OptIn(ExperimentalIoApi::class)
fun SdkBuffer.writeDouble(value: Double) {
    memory.storeDoubleAt(writePosition, value)
    commitWritten(8)
}
