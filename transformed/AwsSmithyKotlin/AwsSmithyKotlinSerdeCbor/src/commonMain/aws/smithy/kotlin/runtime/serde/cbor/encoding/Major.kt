/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBufferedSource

/**
 * Represents CBOR major types (0 for unsigned integer, 1 for negative integer, etc...)
 */
internal enum class Major(val value: UByte) {
    U_INT(0u),
    NEG_INT(1u),
    BYTE_STRING(2u),
    STRING(3u),
    LIST(4u),
    MAP(5u),
    TAG(6u),
    TYPE_7(7u),
    ;

    companion object {
        fun fromValue(value: UByte): Major = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("$value is not a valid Major value.")
    }
}

private val MAJOR_BYTE_MASK: UByte = 0b111u

internal fun peekMajor(buffer: SdkBufferedSource): Major {
    val byte = buffer.peek().readByte().toUByte()
    val major = ((byte.toUInt() shr 5).toUByte()) and MAJOR_BYTE_MASK
    return Major.fromValue(major)
}
