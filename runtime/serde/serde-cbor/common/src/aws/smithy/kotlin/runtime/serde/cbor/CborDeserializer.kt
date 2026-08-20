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
import aws.smithy.kotlin.runtime.serde.cbor.encoding.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

/**
 * Deserializer for CBOR byte payloads
 * @param payload Bytes from which CBOR data is deserialized
 */
public class CborDeserializer(payload: ByteArray) : Deserializer {
    private val buffer = SdkBuffer().apply { write(payload) }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        peekMajor(buffer).also {
            check(it == Major.MAP) { "Expected major ${Major.MAP} for structure, got $it" }
        }

        val expectedLength = deserializeExpectedLength()
        return CborFieldIterator(buffer, expectedLength, descriptor)
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
    // Decode a CBOR integer (major 0 / 1) to a signed [Long] with the same range/overflow checks as before,
    // without allocating a UInt/NegInt wrapper or boxing through a Number.
    private fun decodeSignedLong(): Long {
        val major = peekMajor(buffer)

        val unsigned: ULong = when (major) {
            Major.U_INT -> decodeUInt(buffer)
            Major.NEG_INT -> decodeNegInt(buffer)
            else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR number, got $major.")
        }

        // Convert ULong -> Long, handling potential overflow
        return if (major == Major.NEG_INT) {
            check(unsigned <= Long.MAX_VALUE.toULong() + 1u) { "CBOR number $unsigned exceeds minimum value ${Long.MIN_VALUE}" }
            -(unsigned.toLong())
        } else {
            check(unsigned <= Long.MAX_VALUE.toULong()) { "CBOR number $unsigned exceeds maximum value ${Long.MAX_VALUE}" }
            unsigned.toLong()
        }
    }

    override fun deserializeByte(): Byte {
        val value = decodeSignedLong()
        check(value in (Byte.MIN_VALUE..Byte.MAX_VALUE)) { "$value out of range for Byte" }
        return value.toByte()
    }

    override fun deserializeShort(): Short {
        val value = decodeSignedLong()
        check(value in (Short.MIN_VALUE..Short.MAX_VALUE)) { "$value out of range for Short" }
        return value.toShort()
    }

    override fun deserializeInt(): Int {
        val value = decodeSignedLong()
        check(value in (Int.MIN_VALUE..Int.MAX_VALUE)) { "$value out of range for Int" }
        return value.toInt()
    }

    override fun deserializeLong(): Long = decodeSignedLong()

    override fun deserializeFloat(): Float = when (val major = peekMajor(buffer)) {
        Major.TYPE_7 -> when (val minor = peekMinorByte(buffer)) {
            Minor.FLOAT16.value -> decodeFloat16(buffer)
            Minor.FLOAT32.value -> decodeFloat32(buffer)
            Minor.FLOAT64.value -> decodeFloat64(buffer).toFloat()
            else -> throw DeserializationException("Unexpected minor value $minor decoding CBOR floating point for major type 7.")
        }
        Major.U_INT -> decodeUInt(buffer).toLong().toFloat()
        // Negate the Long before converting (matches the original `-(value.toLong())` semantics, including
        // the Long.MIN_VALUE overflow), NOT the float — `-x.toFloat()` would parse as convert-then-negate.
        Major.NEG_INT -> (-decodeNegInt(buffer).toLong()).toFloat()
        else -> throw DeserializationException("Expected floating point or integer major type for CBOR floating point number, got $major.")
    }

    override fun deserializeDouble(): Double = when (val major = peekMajor(buffer)) {
        Major.TYPE_7 -> when (val minor = peekMinorByte(buffer)) {
            Minor.FLOAT16.value -> decodeFloat16(buffer).toDouble()
            Minor.FLOAT32.value -> decodeFloat32(buffer).toDouble()
            Minor.FLOAT64.value -> decodeFloat64(buffer)
            else -> throw DeserializationException("Unexpected minor value $minor decoding CBOR floating point for major type 7.")
        }
        Major.U_INT -> decodeUInt(buffer).toLong().toDouble()
        // Negate the Long before converting (see deserializeFloat).
        Major.NEG_INT -> (-decodeNegInt(buffer).toLong()).toDouble()
        else -> throw DeserializationException("Expected floating point or integer major type for CBOR floating point number, got $major.")
    }

    override fun deserializeBigInteger(): BigInteger = when (val tag = Tag.decode(buffer).value) {
        is BigNum -> tag.value
        is NegBigNum -> tag.value
        else -> throw DeserializationException("Expected tag ${TagId.BIG_NUM.value} or ${TagId.NEG_BIG_NUM.value} for CBOR bignum, got $tag")
    }

    override fun deserializeBigDecimal(): BigDecimal {
        val tag = Tag.decode(buffer)
        return (tag.value as DecimalFraction).value
    }

    override fun deserializeString(): String = decodeTextStringValue(buffer)

    override fun deserializeBoolean(): Boolean = decodeBooleanValue(buffer)

    override fun deserializeDocument(): Document = throw DeserializationException("Document is not a supported CBOR type.")

    override fun deserializeNull(): Nothing? {
        Null.decode(buffer)
        return null
    }

    override fun deserializeByteArray(): ByteArray = decodeByteStringValue(buffer)

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
    val descriptor: SdkObjectDescriptor,
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
            val nextFieldName = decodeTextStringValue(buffer)
            descriptor
                .fields
                .firstOrNull { it.serialName == nextFieldName }
                ?.index ?: Deserializer.FieldIterator.UNKNOWN_FIELD
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
        skipValue(buffer)
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
