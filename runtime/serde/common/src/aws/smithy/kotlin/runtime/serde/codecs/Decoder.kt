/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.codecs

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.Document

public interface Decoder {
    public fun decodeBigDecimal(): BigDecimal
    public fun decodeBigInteger(): BigInteger
    public fun decodeBoolean(): Boolean
    public fun decodeByte(): Byte
    public fun decodeByteStream(): ByteStream
    public fun decodeDouble(): Double
    public fun decodeFloat(): Float
    public fun decodeInt(): Int
    public fun <T> decodeList(block: (Decoder) -> T): List<T>
    public fun decodeLong(): Long
    public fun <K, V> decodeMap(keyBlock: (Decoder) -> K, valueBlock: (Decoder) -> V): Map<K, V>
    public fun decodeNull()
    public fun decodeShort(): Short
    public fun decodeString(): String
    public fun decodeStructure(block: (String, Decoder) -> Unit)
}

public fun Decoder.decodeDocument(): Document? = TODO("Implement me!")
