/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.cbor.*
import aws.smithy.kotlin.runtime.serde.cbor.writeArgument
import aws.smithy.kotlin.runtime.serde.cbor.encodeMajorMinor

/**
 * Represents a CBOR text string (major type 3) encoded as a UTF-8 byte array.
 * @param value The [TextString] which this CBOR string represents.
 */
internal class TextString(val value: String) : Value {
    override fun encode(into: SdkBufferedSink) {
        // Encode the UTF-8 bytes once and use their length for the CBOR length argument. The CBOR text
        // string length is a *byte* count (RFC 8949 §3.1), so we must not use `value.length` which is a
        // UTF-16 code-unit count and diverges for any multibyte character.
        val bytes = value.encodeToByteArray()
        into.writeArgument(Major.STRING, bytes.size.toULong())
        into.write(bytes)
    }

    internal companion object {
        fun decode(buffer: CborReader, depth: Int = 0): TextString = TextString(decodeValue(buffer, depth))

        // Decode the string value directly, without allocating a [TextString] wrapper. Used on the hot
        // deserialize path (every field name and string value). Read the head byte once (it is always
        // consumed) and derive the length from it.
        fun decodeValue(buffer: CborReader, depth: Int = 0): String {
            val head = buffer.readByte().toUByte()

            if (minorOf(head) != Minor.INDEFINITE.value) {
                val length = decodeArgument(buffer, head).toInt()
                // Decode UTF-8 directly from the cursor (bulk decode over the backing byte array).
                return buffer.readUtf8(length)
            }

            // Indefinite-length text string: concatenate the definite-length chunks until the break code.
            // The head byte has already been consumed above, so read chunks directly instead of routing
            // through IndefiniteList.decode (which would try to discard a head byte again). Mirrors the
            // previous behavior exactly: each chunk is decoded via Value.decode(depth + 1) and cast to
            // TextString.
            val sb = StringBuilder()
            while (!buffer.nextValueIsIndefiniteBreak) {
                sb.append((Value.decode(buffer, depth + 1) as TextString).value)
            }
            IndefiniteBreak.decode(buffer)
            return sb.toString()
        }
    }
}

/**
 * Represents a CBOR byte string (major type 2).
 * @param value The [ByteArray] which this CBOR byte string represents.
 */
internal class ByteString(val value: ByteArray) : Value {
    override fun encode(into: SdkBufferedSink) {
        into.writeArgument(Major.BYTE_STRING, value.size.toULong())
        into.write(value)
    }

    internal companion object {
        fun decode(buffer: CborReader, depth: Int = 0): ByteString = if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
            val chunks = IndefiniteList.decode(buffer, depth).value.map { (it as ByteString).value }

            val out = ByteArray(chunks.sumOf { it.size })
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(out, offset)
                offset += chunk.size
            }
            ByteString(out)
        } else {
            val length = decodeArgument(buffer).toInt()
            // Read the byte string directly from the cursor (bulk slice of the backing byte array).
            ByteString(buffer.readByteArray(length))
        }
    }
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
        internal fun decode(buffer: CborReader, depth: Int = 0): List {
            val length = decodeArgument(buffer).toInt()
            val valuesList = mutableListOf<Value>()

            for (i in 0 until length) {
                valuesList.add(Value.decode(buffer, depth + 1))
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
        // Test-compat overload.
        internal fun decode(buffer: SdkBufferedSource, depth: Int = 0): IndefiniteList = decode(CborReader(buffer), depth)

        internal fun decode(buffer: CborReader, depth: Int = 0): IndefiniteList {
            buffer.readByte() // discard head

            val list = mutableListOf<Value>()

            while (!buffer.nextValueIsIndefiniteBreak) {
                list.add(Value.decode(buffer, depth + 1))
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
        into.writeArgument(Major.MAP, value.size.toULong())
        value.forEach { (k, v) ->
            k.encode(into)
            v.encode(into)
        }
    }

    internal companion object {
        internal fun decode(buffer: CborReader, depth: Int = 0): Map {
            val valueMap = mutableMapOf<Value, Value>()
            val length = decodeArgument(buffer).toInt()

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
        internal fun decode(buffer: CborReader, depth: Int = 0): IndefiniteMap {
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
