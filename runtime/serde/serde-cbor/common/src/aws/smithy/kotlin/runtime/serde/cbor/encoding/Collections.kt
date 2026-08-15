/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.DeserializationRecursionException
import aws.smithy.kotlin.runtime.serde.cbor.*
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor
import aws.smithy.kotlin.runtime.serde.cbor.writeArgument

/**
 * Represents a CBOR text string (major type 3) encoded as a UTF-8 byte array.
 * @param value The [TextString] which this CBOR string represents.
 */
internal class TextString(val value: String) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeTextString(value)

    internal companion object {
        fun decode(buffer: SdkBufferedSource, depth: Int = 0): TextString = TextString(decodeTextStringValue(buffer, depth))
    }
}

internal fun SdkBufferedSink.writeTextString(value: String) {
    val bytes = value.encodeToByteArray()
    writeArgument(Major.STRING, bytes.size.toULong())
    write(bytes)
}

private inline fun SdkBufferedSource.decodeIndefiniteStringChunks(expected: Major, decodeChunk: () -> Unit) {
    readByte() // discard indefinite-length head
    while (!nextValueIsIndefiniteBreak) {
        val major = peekMajor(this)
        if (major != expected) {
            throw DeserializationException("Unexpected major type $major in indefinite-length CBOR string, expected $expected")
        }
        decodeChunk()
    }
    IndefiniteBreak.decode(this)
}

internal fun decodeTextStringValue(buffer: SdkBufferedSource, depth: Int = 0): String {
    DeserializationRecursionException.assertDepth(depth)

    if (peekMinorByte(buffer) != Minor.INDEFINITE.value) {
        val length = decodeArgument(buffer).toLong()
        return buffer.readUtf8(length)
    }

    val sb = StringBuilder()
    buffer.decodeIndefiniteStringChunks(Major.STRING) { sb.append(decodeTextStringValue(buffer, depth + 1)) }
    return sb.toString()
}

/**
 * Represents a CBOR byte string (major type 2).
 * @param value The [ByteArray] which this CBOR byte string represents.
 */
internal class ByteString(val value: ByteArray) : Value {
    override fun encode(into: SdkBufferedSink) = into.writeByteString(value)

    internal companion object {
        fun decode(buffer: SdkBufferedSource, depth: Int = 0): ByteString = ByteString(decodeByteStringValue(buffer, depth))
    }
}

internal fun SdkBufferedSink.writeByteString(value: ByteArray) {
    writeArgument(Major.BYTE_STRING, value.size.toULong())
    write(value)
}

internal fun decodeByteStringValue(buffer: SdkBufferedSource, depth: Int = 0): ByteArray {
    DeserializationRecursionException.assertDepth(depth)

    if (peekMinorByte(buffer) != Minor.INDEFINITE.value) {
        val length = decodeArgument(buffer).toLong()
        return buffer.readByteArray(length)
    }

    val tempBuffer = SdkBuffer()
    buffer.decodeIndefiniteStringChunks(Major.BYTE_STRING) { tempBuffer.write(decodeByteStringValue(buffer, depth + 1)) }
    return tempBuffer.readByteArray()
}

/**
 * Represents a CBOR list (major type 4).
 * @param value the [kotlin.collections.List<Value>] represented by this CBOR list.
 */
internal class List(val value: kotlin.collections.List<Value>) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeArgument(Major.LIST, value.size.toULong())
        value.forEach { it.encode(into) }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): List = List(decodeListValue(buffer, depth))
    }
}

internal fun decodeListValue(buffer: SdkBufferedSource, depth: Int = 0): kotlin.collections.List<Value> {
    val length = decodeArgument(buffer).toLong()
    val valuesList = mutableListOf<Value>()

    for (i in 0 until length) {
        valuesList.add(Value.decode(buffer, depth + 1))
    }

    return valuesList
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
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): IndefiniteList = IndefiniteList(decodeIndefiniteListValue(buffer, depth))
    }
}

internal fun decodeIndefiniteListValue(buffer: SdkBufferedSource, depth: Int = 0): kotlin.collections.List<Value> {
    buffer.readByte() // discard head

    val list = mutableListOf<Value>()

    while (!buffer.nextValueIsIndefiniteBreak) {
        list.add(Value.decode(buffer, depth + 1))
    }

    IndefiniteBreak.decode(buffer)
    return list
}

/**
 * Represents a CBOR map (major type 5).
 * @param value The [kotlin.collections.Map] that this CBOR map represents.
 */
internal class Map(val value: kotlin.collections.Map<Value, Value>) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeArgument(Major.MAP, value.size.toULong())
        value.forEach { (k, v) ->
            k.encode(into)
            v.encode(into)
        }
    }

    internal companion object {
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): Map {
            val valueMap = mutableMapOf<Value, Value>()
            val length = decodeArgument(buffer).toLong()

            for (i in 0 until length) {
                val key = Value.decode(buffer, depth + 1)
                val value = Value.decode(buffer, depth + 1)
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
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): IndefiniteMap {
            buffer.readByte() // discard head byte
            val valueMap = mutableMapOf<Value, Value>()

            while (!buffer.nextValueIsIndefiniteBreak) {
                val key = Value.decode(buffer, depth + 1)
                val value = Value.decode(buffer, depth + 1)
                valueMap[key] = value
            }

            IndefiniteBreak.decode(buffer)
            return IndefiniteMap(valueMap)
        }
    }
}
