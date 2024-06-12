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
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

public class CborDeserializer(payload: ByteArray) : Deserializer {
    private val buffer = SdkBuffer().apply { write(payload) }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        val major = peekMajor(buffer)
        check(major == Major.MAP) { "Expected major ${Major.MAP} for structure, got $major." }

        val expectedLength = if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
            buffer.readByte() // no length encoded, discard head
            null
        } else {
            deserializeArgument(buffer)
        }

        return CborFieldIterator(buffer, expectedLength, descriptor)
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        val major = peekMajor(buffer)
        check(major == Major.LIST) { "Expected major ${Major.LIST} for CBOR list, got $major" }

        val expectedLength = if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
            buffer.readByte()
            null
        } else {
            deserializeArgument(buffer)
        }

        return CborElementIterator(buffer, expectedLength)
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        val major = peekMajor(buffer)
        check(major == Major.MAP) { "Expected major ${Major.MAP} for CBOR map, got $major" }

        val expectedLength = if (peekMinorByte(buffer) == Minor.INDEFINITE.value) {
            buffer.readByte()
            null
        } else {
            deserializeArgument(buffer)
        }

        return CborEntryIterator(buffer, expectedLength)
    }
}

internal class CborPrimitiveDeserializer(private val buffer: SdkBufferedSource) : PrimitiveDeserializer {
    override fun deserializeByte(): Byte = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toByte()
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer).value.toByte()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR byte, got $major.")
    }

    override fun deserializeInt(): Int = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toInt()
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer).value.toInt()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR integer, got $major.")
    }

    override fun deserializeShort(): Short = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toShort()
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer).value.toShort()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR short, got $major.")
    }

    override fun deserializeLong(): Long = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toLong()
        Major.NEG_INT -> Cbor.Encoding.NegInt.decode(buffer).value
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR short, got $major.")
    }

    override fun deserializeFloat(): Float = when (peekMinorByte(buffer)) {
        Minor.FLOAT16.value -> Cbor.Encoding.Float16.decode(buffer).value
        Minor.FLOAT32.value -> Cbor.Encoding.Float32.decode(buffer).value
        Minor.FLOAT64.value -> Cbor.Encoding.Float64.decode(buffer).value.toFloat()
        else -> Float.fromBits(deserializeArgument(buffer).toInt())
    }

    override fun deserializeDouble(): Double = when (peekMinorByte(buffer)) {
        Minor.FLOAT16.value -> Cbor.Encoding.Float16.decode(buffer).value.toDouble()
        Minor.FLOAT32.value -> Cbor.Encoding.Float32.decode(buffer).value.toDouble()
        Minor.FLOAT64.value -> Cbor.Encoding.Float64.decode(buffer).value
        else -> Double.fromBits(deserializeArgument(buffer).toLong())
    }

    override fun deserializeBigInteger(): BigInteger = when (val tagId = peekTag(buffer).id.toUInt()) {
        2u -> Cbor.Encoding.BigNum.decode(buffer).value
        3u -> Cbor.Encoding.NegBigNum.decode(buffer).value
        else -> throw DeserializationException("Expected tag 2 or 3 for CBOR BigNum, got $tagId")
    }

    override fun deserializeBigDecimal(): BigDecimal = Cbor.Encoding.DecimalFraction.decode(buffer).value

    override fun deserializeString(): String = Cbor.Encoding.String.decode(buffer).value

    override fun deserializeBoolean(): Boolean = Cbor.Encoding.Boolean.decode(buffer).value

    override fun deserializeDocument(): Document { throw DeserializationException("Document is not a supported CBOR type.") }

    override fun deserializeNull(): Nothing? {
        Cbor.Encoding.Null.decode(buffer)
        return null
    }

    override fun deserializeBlob(): ByteArray = Cbor.Encoding.ByteString.decode(buffer).value

    override fun deserializeTimestamp(format: TimestampFormat): Instant = Cbor.Encoding.Timestamp.decode(buffer).value
}

/**
 * Element iterator used for deserializing lists
 */
private class CborElementIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong? = null,
) : Deserializer.ElementIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    val primitiveDeserializer = CborPrimitiveDeserializer(buffer)
    var currentLength = 0uL

    override fun hasNextElement(): Boolean {
        if (expectedLength != null) {
            if (currentLength != expectedLength) {
                check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted, read $currentLength elements, expected $expectedLength." }
                return true
            } else {
                return !buffer.exhausted()
            }
        } else {
            val peekedNextValue = decodeNextValue(buffer.peek())
            return if (peekedNextValue is Cbor.Encoding.IndefiniteBreak) {
                Cbor.Encoding.IndefiniteBreak.decode(buffer)
                false
            } else {
                check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted" }
                true
            }
        }
    }

    override fun nextHasValue(): Boolean {
        val value = decodeNextValue(buffer.peek())
        return (value !is Cbor.Encoding.Null)
    }

    override fun deserializeBoolean(): Boolean = primitiveDeserializer.deserializeBoolean().also { currentLength += 1u }
    override fun deserializeBigInteger(): BigInteger = primitiveDeserializer.deserializeBigInteger().also { currentLength += 1u }
    override fun deserializeBigDecimal(): BigDecimal = primitiveDeserializer.deserializeBigDecimal().also { currentLength += 1u }
    override fun deserializeByte(): Byte = primitiveDeserializer.deserializeByte().also { currentLength += 1u }
    override fun deserializeDocument(): Document = primitiveDeserializer.deserializeDocument().also { currentLength += 1u }
    override fun deserializeDouble(): Double = primitiveDeserializer.deserializeDouble().also { currentLength += 1u }
    override fun deserializeFloat(): Float = primitiveDeserializer.deserializeFloat().also { currentLength += 1u }
    override fun deserializeInt(): Int = primitiveDeserializer.deserializeInt().also { currentLength += 1u }
    override fun deserializeLong(): Long = primitiveDeserializer.deserializeLong().also { currentLength += 1u }
    override fun deserializeNull(): Nothing? = primitiveDeserializer.deserializeNull().also { currentLength += 1u }
    override fun deserializeShort(): Short = primitiveDeserializer.deserializeShort().also { currentLength += 1u }
    override fun deserializeString(): String = primitiveDeserializer.deserializeString().also { currentLength += 1u }
    override fun deserializeBlob(): ByteArray = primitiveDeserializer.deserializeBlob().also { currentLength += 1u }
    override fun deserializeTimestamp(format: TimestampFormat): Instant = primitiveDeserializer.deserializeTimestamp(format).also { currentLength += 1u }
}

/**
 * Field iterator used for deserializing structures
 */
private class CborFieldIterator(
    val buffer: SdkBuffer,
    val expectedLength: ULong? = null,
    val descriptor: SdkObjectDescriptor,
) : Deserializer.FieldIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    var currentLength: ULong = 0uL

    override fun findNextFieldIndex(): Int? {
        if (expectedLength == currentLength || buffer.exhausted()) { return null }
        val peekedNextValue = decodeNextValue(buffer.peek())
        return if (peekedNextValue is Cbor.Encoding.IndefiniteBreak) {
            Cbor.Encoding.IndefiniteBreak.decode(buffer)
            null
        } else {
            val nextFieldName = Cbor.Encoding.String.decode(buffer).value
            return descriptor
                .fields
                .firstOrNull { it.serialName.equals(nextFieldName, ignoreCase = true) }
                ?.index
        }.also { currentLength += 1uL }
    }

    override fun skipValue() { decodeNextValue(buffer) }
}

/**
 * Entry iterator used for deserializing maps
 */
private class CborEntryIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong?,
) : Deserializer.EntryIterator, PrimitiveDeserializer {
    private var currentLength = 0uL
    private val primitiveDeserializer = CborPrimitiveDeserializer(buffer)

    override fun hasNextEntry(): Boolean {
        if (expectedLength != null) {
            if (currentLength != expectedLength) {
                check(!buffer.exhausted()) { "Buffer unexpectedly exhausted" }
            } else {
                return false
            }
        }

        val peekedNextKey = decodeNextValue(buffer.peek())
        return if (peekedNextKey is Cbor.Encoding.IndefiniteBreak) {
            Cbor.Encoding.IndefiniteBreak.decode(buffer)
            false
        } else if (peekedNextKey is Cbor.Encoding.Null) {
            Cbor.Encoding.Null.decode(buffer)
            false
        } else {
            check(peekedNextKey is Cbor.Encoding.String) { "Expected string type for CBOR map key, got $peekedNextKey" }
            true
        }
    }

    override fun key(): String = Cbor.Encoding.String.decode(buffer).value

    override fun nextHasValue(): Boolean {
        val peekedNextValue = decodeNextValue(buffer.peek())
        return peekedNextValue !is Cbor.Encoding.Null
    }

    override fun deserializeBoolean(): Boolean = primitiveDeserializer.deserializeBoolean().also { currentLength += 1u }
    override fun deserializeBigInteger(): BigInteger = primitiveDeserializer.deserializeBigInteger().also { currentLength += 1u }
    override fun deserializeBigDecimal(): BigDecimal = primitiveDeserializer.deserializeBigDecimal().also { currentLength += 1u }
    override fun deserializeByte(): Byte = primitiveDeserializer.deserializeByte().also { currentLength += 1u }
    override fun deserializeDocument(): Document = primitiveDeserializer.deserializeDocument().also { currentLength += 1u }
    override fun deserializeDouble(): Double = primitiveDeserializer.deserializeDouble().also { currentLength += 1u }
    override fun deserializeFloat(): Float = primitiveDeserializer.deserializeFloat().also { currentLength += 1u }
    override fun deserializeInt(): Int = primitiveDeserializer.deserializeInt().also { currentLength += 1u }
    override fun deserializeLong(): Long = primitiveDeserializer.deserializeLong().also { currentLength += 1u }
    override fun deserializeNull(): Nothing? = primitiveDeserializer.deserializeNull().also { currentLength += 1u }
    override fun deserializeShort(): Short = primitiveDeserializer.deserializeShort().also { currentLength += 1u }
    override fun deserializeString(): String = primitiveDeserializer.deserializeString().also { currentLength += 1u }
    override fun deserializeTimestamp(format: TimestampFormat): Instant = primitiveDeserializer.deserializeTimestamp(format).also { currentLength += 1u }
    override fun deserializeBlob(): ByteArray = primitiveDeserializer.deserializeBlob().also { currentLength += 1u }
}
