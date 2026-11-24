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
}

// Extract the [Major] type from an already-read head byte. The high 3 bits always fall in 0..7, which
// map directly to enum ordinals, so we index [Major.entries] and avoid a per-call linear scan.
internal fun majorOf(head: UByte): Major = Major.entries[head.toInt() shr 5]

internal fun peekMajor(buffer: SdkBufferedSource): Major = majorOf(peekByte(buffer))
