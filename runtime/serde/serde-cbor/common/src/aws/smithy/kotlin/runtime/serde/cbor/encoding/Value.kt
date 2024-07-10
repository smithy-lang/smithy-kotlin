/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException

/**
 * Represents an encodable / decodable CBOR value.
 */
internal interface Value {
    /**
     * Encode this [Value] by writing its bytes [into] an [SdkBuffer]
     * @param into the [SdkBufferedSink] to encode into
     */
    fun encode(into: SdkBufferedSink)

    companion object {
        /**
         * Decode a [Value] from the given [buffer]
         * @param buffer the [SdkBufferedSource] to read the next [Value] from
         */
        fun decode(buffer: SdkBufferedSource): Value {
            val major = peekMajor(buffer)
            val minor = peekMinorByte(buffer)

            return when (major) {
                Major.U_INT -> UInt.decode(buffer)
                Major.NEG_INT -> NegInt.decode(buffer)
                Major.BYTE_STRING -> ByteString.decode(buffer)
                Major.STRING -> TextString.decode(buffer)
                Major.LIST -> {
                    return if (minor == Minor.INDEFINITE.value) {
                        IndefiniteList.decode(buffer)
                    } else {
                        List.decode(buffer)
                    }
                }
                Major.MAP -> {
                    if (minor == Minor.INDEFINITE.value) {
                        IndefiniteMap.decode(buffer)
                    } else {
                        Map.decode(buffer)
                    }
                }
                Major.TAG -> Tag.decode(buffer)
                Major.TYPE_7 -> {
                    when (minor) {
                        Minor.TRUE.value -> Boolean.decode(buffer)
                        Minor.FALSE.value -> Boolean.decode(buffer)
                        Minor.NULL.value -> Null.decode(buffer)
                        Minor.UNDEFINED.value -> Null.decode(buffer)
                        Minor.FLOAT16.value -> Float16.decode(buffer)
                        Minor.FLOAT32.value -> Float32.decode(buffer)
                        Minor.FLOAT64.value -> Float64.decode(buffer)
                        Minor.INDEFINITE.value -> IndefiniteBreak.decode(buffer)
                        else -> throw DeserializationException("Unexpected type 7 minor value $minor")
                    }
                }
            }
        }
    }
}
