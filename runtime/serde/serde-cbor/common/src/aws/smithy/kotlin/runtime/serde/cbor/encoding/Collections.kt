/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.cbor.*
import aws.smithy.kotlin.runtime.serde.cbor.encodeArgument
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor

/**
 * Represents a CBOR text string (major type 3) encoded as a UTF-8 byte array.
 * @param value The [TextString] which this CBOR string represents.
 */
internal class TextString(val value: String) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.write(encodeArgument(Major.STRING, value.length.toULong()))
        into.write(value.encodeToByteArray())
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): TextString =
            if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
                val list = IndefiniteList.decode(buffer).value

                val sb = StringBuilder()
                list.forEach {
                    sb.append((it as TextString).value)
                }

                TextString(sb.toString())
            } else {
                val length = decodeArgument(buffer).toInt()

                val bytes = SdkBuffer().use {
                    buffer.readFully(it, length.toLong())
                    it.readByteArray()
                }

                TextString(bytes.decodeToString())
            }
    }
}

/**
 * Represents a CBOR byte string (major type 2).
 * @param value The [ByteArray] which this CBOR byte string represents.
 */
internal class ByteString(val value: ByteArray) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.write(encodeArgument(Major.BYTE_STRING, value.size.toULong()))
        into.write(value)
    }

    internal companion object {
        fun decode(buffer: SdkBufferedSource): ByteString =
            if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
                val list = IndefiniteList.decode(buffer).value

                val tempBuffer = SdkBuffer()
                list.forEach {
                    tempBuffer.write((it as ByteString).value)
                }

                ByteString(tempBuffer.readByteArray())
            } else {
                val length = decodeArgument(buffer).toInt()

                val bytes = SdkBuffer().use {
                    buffer.readFully(it, length.toLong())
                    it.readByteArray()
                }

                ByteString(bytes)
            }
    }
}

/**
 * Represents a CBOR list (major type 4).
 * @param value the [kotlin.collections.List<Value>] represented by this CBOR list.
 */
internal class List(val value: kotlin.collections.List<Value>) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.write(encodeArgument(Major.LIST, value.size.toULong()))
        value.forEach { it.encode(into) }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): List {
            val length = decodeArgument(buffer).toInt()
            val valuesList = mutableListOf<Value>()

            for (i in 0 until length) {
                valuesList.add(Value.decode(buffer))
            }

            return List(valuesList)
        }
    }
}

/**
 * Represents a CBOR list with an indefinite length (major type 4, minor type 31).
 * @param value The optional [MutableList] that this CBOR indefinite list represents. This value is mainly
 * used for storing a list of decoded values.
 *
 * Note: `encode` will just *begin* encoding the list, callers are expected to:
 * - call `encode` for each [Value] in the list
 * - end the list by sending an [IndefiniteBreak]
 *
 * `decode` will consume list values until an [IndefiniteBreak] is encountered.
 */
internal class IndefiniteList(val value: Collection<Value> = listOf()) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeByte(encodeMajorMinor(Major.LIST, Minor.INDEFINITE))
        value.forEach {
            it.encode(into)
        }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): IndefiniteList {
            buffer.readByte() // discard head

            val list = mutableListOf<Value>()

            while (!buffer.nextValueIsIndefiniteBreak) {
                list.add(Value.decode(buffer))
            }

            IndefiniteBreak.decode(buffer)
            return IndefiniteList(list)
        }
    }
}

/**
 * Represents a CBOR map (major type 5).
 * @param value The [kotlin.collections.Map] that this CBOR map represents.
 */
internal class Map(val value: kotlin.collections.Map<Value, Value>) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.write(encodeArgument(Major.MAP, value.size.toULong()))
        value.forEach { (k, v) ->
            k.encode(into)
            v.encode(into)
        }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): Map {
            val valueMap = mutableMapOf<Value, Value>()
            val length = decodeArgument(buffer).toInt()

            for (i in 0 until length) {
                val key = Value.decode(buffer)
                val value = Value.decode(buffer)
                valueMap[key] = value
            }

            return Map(valueMap)
        }
    }
}

/**
 * Represents a CBOR map with indefinite length (major type 5, minor type 31).
 * @param value The optional [MutableMap] that this CBOR indefinite map represents. This value is mainly
 * used for storing the decoded entries of the map.
 *
 * Note: `encode` will just *begin* encoding the map, callers are expected to:
 * - call `encode` for each [TextString]/[Value] value pair in the map
 * - end the map by sending an [IndefiniteBreak]
 *
 * `decode` will consume map entries until an [IndefiniteBreak] is encountered.
 */
internal class IndefiniteMap(val value: kotlin.collections.Map<Value, Value> = mapOf()) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeByte(encodeMajorMinor(Major.MAP, Minor.INDEFINITE))
        value.entries.forEach { (k, v) ->
            k.encode(into)
            v.encode(into)
        }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource): IndefiniteMap {
            buffer.readByte() // discard head byte
            val valueMap = mutableMapOf<Value, Value>()

            while (!buffer.nextValueIsIndefiniteBreak) {
                val key = Value.decode(buffer)
                val value = Value.decode(buffer)
                valueMap[key] = value
            }

            IndefiniteBreak.decode(buffer)
            return IndefiniteMap(valueMap)
        }
    }
}
