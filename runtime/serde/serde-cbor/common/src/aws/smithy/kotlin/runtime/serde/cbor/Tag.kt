/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBufferedSource

internal enum class TagId(val value: ULong) {
    TIMESTAMP(1uL),
    BIG_NUM(2uL),
    NEG_BIG_NUM(3uL),
    DECIMAL_FRACTION(4uL),
}

internal fun peekTag(buffer: SdkBufferedSource) = Cbor.Encoding.Tag.decode(buffer.peek())
