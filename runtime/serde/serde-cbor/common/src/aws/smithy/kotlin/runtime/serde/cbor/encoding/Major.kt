/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

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

// Derive the [Major] type from an already-read head byte. The top 3 bits index directly into
// Major.entries (values 0..7 in ordinal order), avoiding the linear scan in Major.fromValue.
internal fun majorOf(head: UByte): Major = Major.entries[((head.toInt() shr 5) and 0b111)]
