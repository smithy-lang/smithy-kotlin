/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.BigNum
import aws.smithy.kotlin.runtime.serde.cbor.encoding.ByteString
import aws.smithy.kotlin.runtime.serde.cbor.encoding.DecimalFraction
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Float16
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Float32
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Float64
import aws.smithy.kotlin.runtime.serde.cbor.encoding.IndefiniteBreak
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Minor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.NegBigNum
import aws.smithy.kotlin.runtime.serde.cbor.encoding.NegInt
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Null
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Tag
import aws.smithy.kotlin.runtime.serde.cbor.encoding.TagId
import aws.smithy.kotlin.runtime.serde.cbor.encoding.TextString
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Timestamp
import aws.smithy.kotlin.runtime.serde.cbor.encoding.UInt
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Value
import aws.smithy.kotlin.runtime.serde.cbor.encoding.decodeArgument
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMajor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.peekMinorByte
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Boolean as cborBoolean

/**
 * Deserializer for CBOR byte payloads
 * @param payload Bytes from which CBOR data is deserialized
 */
public class CborDeserializer(payload: ByteArray) : Deserializer {
    private val buffer = SdkBuffer().apply { write(payload) }

    // Memoizes a serialName -> field index lookup table per struct descriptor so that field
    // resolution during deserialization is O(1) instead of a linear scan (which also re-resolves
    // the CborSerialName trait on every comparison). A single CborDeserializer instance decodes one
    // payload on one thread, so this cache requires no synchronization and is reused across every
    // (possibly repeated/nested) struct decoded from that payload.
    private val fieldIndexCache = HashMap<SdkObjectDescriptor, Map<String, Int>>()

    private fun fieldIndex(descriptor: SdkObjectDescriptor): Map<String, Int> = fieldIndexCache.getOrPut(descriptor) {
        val index = HashMap<String, Int>(descriptor.fields.size)
        descriptor.fields.forEach { index[it.serialName] = it.index }
        index
    }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        peekMajor(buffer).also {
            check(it == Major.MAP) { "Expected major ${Major.MAP} for structure, got $it" }
        }

        val expectedLength = deserializeExpectedLength()
        return CborFieldIterator(buffer, expectedLength, fieldIndex(descriptor))
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        peekMajor(buffer).also {
            check(it == Major.MAP) { "Expected major ${Major.MAP} for CBOR map, got $it" }
        }

        val expectedLength = deserializeExpectedLength()
        return CborEntryIterator(buffer, expectedLength)
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        peekMajor(buffer).also {
            check(it == Major.LIST) { "Expected major ${Major.LIST} for CBOR list, got $it" }
        }

        val expectedLength = deserializeExpectedLength()
        return CborElementIterator(buffer, expectedLength)
    }

    // Peek at the head byte and return the expected length of the list/map if provided, null if not
    private fun deserializeExpectedLength(): ULong? = if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
        buffer.readByte() // no length encoded, discard head
        null
    } else {
        decodeArgument(buffer)
    }
}

internal class CborPrimitiveDeserializer(private val buffer: SdkBufferedSource) : PrimitiveDeserializer {
    private inline fun <reified T : Number> deserializeNumber(cast: (Number) -> T): T {
        val major = peekMajor(buffer)

        val unsigned: ULong = when (major) {
            Major.U_INT -> UInt.decode(buffer).value
            Major.NEG_INT -> NegInt.decode(buffer).value
            else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR number, got $major.")
        }

        // Convert ULong -> Long, handling potential overflow
        val signed: Long = if (major == Major.NEG_INT) {
            check(unsigned <= Long.MAX_VALUE.toULong() + 1u) { "CBOR number $unsigned exceeds minimum value ${Long.MIN_VALUE}" }
            -(unsigned.toLong())
        } else {
            check(unsigned <= Long.MAX_VALUE.toULong()) { "CBOR number $unsigned exceeds maximum value ${Long.MAX_VALUE}" }
            unsigned.toLong()
        }

        when (T::class) {
            Byte::class -> check(signed in (Byte.MIN_VALUE..Byte.MAX_VALUE)) { "$signed out of range for Byte" }
            Short::class -> check(signed in (Short.MIN_VALUE..Short.MAX_VALUE)) { "$signed out of range for Short" }
            Int::class -> check(signed in (Int.MIN_VALUE..Int.MAX_VALUE)) { "$signed out of range for Int" }
        }

        return cast(signed)
    }

    override fun deserializeByte(): Byte = deserializeNumber { it.toByte() }
    override fun deserializeInt(): Int = deserializeNumber { it.toInt() }
    override fun deserializeShort(): Short = deserializeNumber { it.toShort() }
    override fun deserializeLong(): Long = deserializeNumber { it.toLong() }

    private inline fun <reified T : Number> deserializeFloatingPoint(cast: (Number) -> T): T {
        val number: Number = when (val major = peekMajor(buffer)) {
            Major.TYPE_7 -> when (val minor = peekMinorByte(buffer)) {
                Minor.FLOAT16.value -> Float16.decode(buffer).value
                Minor.FLOAT32.value -> Float32.decode(buffer).value
                Minor.FLOAT64.value -> Float64.decode(buffer).value
                else -> throw DeserializationException("Unexpected minor value $minor decoding CBOR floating point for major type 7.")
            }
            Major.U_INT -> UInt.decode(buffer).value.toLong()
            Major.NEG_INT -> -(NegInt.decode(buffer).value.toLong())
            else -> throw DeserializationException("Expected floating point or integer major type for CBOR floating point number, got $major.")
        }
        return cast(number)
    }

    override fun deserializeFloat(): Float = deserializeFloatingPoint { it.toFloat() }
    override fun deserializeDouble(): Double = deserializeFloatingPoint { it.toDouble() }

    override fun deserializeBigInteger(): BigInteger = when (val tag = Tag.decode(buffer).value) {
        is BigNum -> tag.value
        is NegBigNum -> tag.value
        else -> throw DeserializationException("Expected tag ${TagId.BIG_NUM.value} or ${TagId.NEG_BIG_NUM.value} for CBOR bignum, got $tag")
    }

    override fun deserializeBigDecimal(): BigDecimal {
        val tag = Tag.decode(buffer)
        return (tag.value as DecimalFraction).value
    }

    override fun deserializeString(): String = TextString.decode(buffer).value

    override fun deserializeBoolean(): Boolean = cborBoolean.decode(buffer).value

    override fun deserializeDocument(): Document = throw DeserializationException("Document is not a supported CBOR type.")

    override fun deserializeNull(): Nothing? {
        Null.decode(buffer)
        return null
    }

    override fun deserializeByteArray(): ByteArray = ByteString.decode(buffer).value

    override fun deserializeInstant(format: TimestampFormat): Instant {
        val tag = Tag.decode(buffer)
        return (tag.value as Timestamp).value
    }
}

/**
 * Element iterator used for deserializing lists
 */
private class CborElementIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong? = null,
) : Deserializer.ElementIterator,
    PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    var currentLength = 0uL

    override fun hasNextElement(): Boolean {
        if (expectedLength != null) {
            if (currentLength != expectedLength) {
                check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted, read $currentLength elements, expected $expectedLength." }
                currentLength += 1u // FIXME hasNextElement should be treated as a read-only operation, free from side effects
                return true
            } else {
                return false
            }
        } else {
            return if (buffer.nextValueIsIndefiniteBreak) {
                IndefiniteBreak.decode(buffer)
                false
            } else {
                check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted" }
                true
            }
        }
    }

    override fun nextHasValue(): Boolean = !buffer.nextValueIsNull
}

/**
 * Field iterator used for deserializing structures
 */
private class CborFieldIterator(
    val buffer: SdkBuffer,
    val expectedLength: ULong? = null,
    val fieldIndexByName: Map<String, Int>,
) : Deserializer.FieldIterator,
    PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    var currentLength: ULong = 0uL

    override tailrec fun findNextFieldIndex(): Int? {
        if (buffer.exhausted() && expectedLength != currentLength) {
            throw DeserializationException("Buffer is unexpectedly exhausted, expected $expectedLength elements, got $currentLength")
        } else if (expectedLength == currentLength) {
            return null
        }
        currentLength += 1uL

        val candidate: Int? = if (buffer.nextValueIsIndefiniteBreak) {
            if (expectedLength != null) {
                throw DeserializationException("Received unexpected indefinite break while deserializing structure, expected $expectedLength elements, got $currentLength")
            }
            IndefiniteBreak.decode(buffer)
            null
        } else {
            val nextFieldName = TextString.decode(buffer).value
            fieldIndexByName[nextFieldName] ?: Deserializer.FieldIterator.UNKNOWN_FIELD
        }

        if (candidate != null) {
            // skip explicit null values
            if (buffer.nextValueIsNull) {
                skipValue()
                return findNextFieldIndex()
            }
        }

        return candidate
    }

    override fun skipValue() {
        Value.decode(buffer)
    }
}

/**
 * Entry iterator used for deserializing maps
 */
private class CborEntryIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong?,
) : Deserializer.EntryIterator,
    PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    private var currentLength = 0uL

    override fun hasNextEntry(): Boolean {
        if (expectedLength != null) {
            return if (currentLength != expectedLength) {
                check(!buffer.exhausted()) { "Buffer unexpectedly exhausted, expected $expectedLength elements, read $currentLength" }
                true
            } else {
                false
            }
        }

        return if (buffer.nextValueIsIndefiniteBreak) {
            IndefiniteBreak.decode(buffer)
            false
        } else {
            check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted" }
            true
        }
    }

    override fun key(): String = deserializeString().also { currentLength += 1uL }

    override fun nextHasValue(): Boolean = !buffer.nextValueIsNull
}
