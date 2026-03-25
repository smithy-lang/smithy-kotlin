/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.codecs

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.Document

public interface Encoder {
    public fun encodeBigDecimal(value: BigDecimal)
    public fun encodeBigInteger(value: BigInteger)
    public fun encodeBoolean(value: Boolean)
    public fun encodeByte(value: Byte)
    public fun encodeByteStream(value: ByteStream)
    public fun encodeDouble(value: Double)
    public fun encodeFloat(value: Float)
    public fun encodeInt(value: Int)
    public fun encodeList(elementBlock: (Encoder) -> Unit)
    public fun encodeLong(value: Long)
    public fun encodeMap(entryBlock: (KeyValueEncoder) -> Unit)
    public fun encodeNull()
    public fun encodeShort(value: Short)
    public fun encodeString(value: String)
    public fun encodeStructure(memberBlock: (KeyValueEncoder) -> Unit)
}

public fun Encoder.encodeDocument(value: Document?) {
    when (value) {
        null -> encodeNull()

        is Document.Number -> when (val number = value.value) {
            is BigDecimal -> encodeBigDecimal(number)
            is BigInteger -> encodeBigInteger(number)
            is Byte -> encodeByte(number)
            is Double -> encodeDouble(number)
            is Float -> encodeFloat(number)
            is Int -> encodeInt(number)
            is Long -> encodeLong(number)
            is Short -> encodeShort(number)
            else -> error("Unrecognized number type ${number::class}")
        }

        is Document.Boolean -> encodeBoolean(value.value)
        is Document.String -> encodeString(value.value)

        is Document.List -> encodeList { elementEncoder ->
            value.value.forEach { element -> elementEncoder.encodeDocument(element) }
        }

        is Document.Map -> encodeMap { entryEncoder ->
            value.value.forEach { (key, value) ->
                entryEncoder.encodeEntry(
                    { keyEncoder -> keyEncoder.encodeString(key) },
                    { valueEncoder -> valueEncoder.encodeDocument(value) },
                )
            }
        }
    }
}

public interface KeyValueEncoder {
    public fun encodeEntry(keyBlock: (Encoder) -> Unit, valueBlock: (Encoder) -> Unit)
}
