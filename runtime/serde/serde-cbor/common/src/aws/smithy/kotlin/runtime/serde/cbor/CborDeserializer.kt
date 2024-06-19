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
    private inline fun <reified T : Number> deserializeNumber(cast: (Number) -> T): T =
        when (val major = peekMajor(buffer)) {
            Major.U_INT -> cast(Cbor.Encoding.UInt.decode(buffer).value.toLong())
            Major.NEG_INT -> cast(Cbor.Encoding.NegInt.decode(buffer).value)
            else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR number, got $major.")
        }

    override fun deserializeByte(): Byte = deserializeNumber { it.toByte() }
    override fun deserializeInt(): Int = deserializeNumber { it.toInt() }
    override fun deserializeShort(): Short = deserializeNumber { it.toShort() }
    override fun deserializeLong(): Long = deserializeNumber { it.toLong() }

    private inline fun <reified T : Number> deserializeFloatingPoint(cast: (Number) -> T): T {
        val number = when (peekMinorByte(buffer)) {
            Minor.FLOAT16.value -> Cbor.Encoding.Float16.decode(buffer).value
            Minor.FLOAT32.value -> Cbor.Encoding.Float32.decode(buffer).value
            Minor.FLOAT64.value -> Cbor.Encoding.Float64.decode(buffer).value
            else -> {
                when (T::class) {
                    Float::class -> Float.fromBits(decodeArgument(buffer).toInt())
                    Double::class -> Double.fromBits(decodeArgument(buffer).toLong())
                    else -> throw DeserializationException("Unsupported floating point type: ${T::class}")
                }
            }
        }
        return cast(number)
    }

    override fun deserializeFloat(): Float = deserializeFloatingPoint { it.toFloat() }
    override fun deserializeDouble(): Double = deserializeFloatingPoint { it.toDouble() }

    override fun deserializeBigInteger(): BigInteger = when (val tagId = peekTag(buffer).id) {
        2uL -> Cbor.Encoding.BigNum.decode(buffer).value
        3uL -> Cbor.Encoding.NegBigNum.decode(buffer).value
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

    override fun deserializeByteArray(): ByteArray = Cbor.Encoding.ByteString.decode(buffer).value

    override fun deserializeInstant(format: TimestampFormat): Instant = Cbor.Encoding.Timestamp.decode(buffer).value
}

/**
 * Element iterator used for deserializing lists
 */
private class CborElementIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong? = null,
) : Deserializer.ElementIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    var currentLength = 0uL

    override fun hasNextElement(): Boolean {
        if (expectedLength != null) {
            if (currentLength != expectedLength) {
                check(!buffer.exhausted()) { "Buffer is unexpectedly exhausted, read $currentLength elements, expected $expectedLength." }
                currentLength += 1u
                return true
            } else {
                return false
            }
        } else {
            return if (buffer.nextValueIsIndefiniteBreak) {
                Cbor.Encoding.IndefiniteBreak.decode(buffer)
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
) : Deserializer.FieldIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    var currentLength: ULong = 0uL

    override fun findNextFieldIndex(): Int? {
        if (expectedLength == currentLength || buffer.exhausted()) { return null }
        currentLength += 1uL

        val candidate: Int? = if (buffer.nextValueIsIndefiniteBreak) {
            Cbor.Encoding.IndefiniteBreak.decode(buffer)
            null
        } else {
            val nextFieldName = Cbor.Encoding.String.decode(buffer).value
            descriptor
                .fields
                .firstOrNull { it.serialName.equals(nextFieldName, ignoreCase = true) }
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

    override fun skipValue() { Cbor.Value.decode(buffer) }
}

/**
 * Entry iterator used for deserializing maps
 */
private class CborEntryIterator(
    val buffer: SdkBufferedSource,
    val expectedLength: ULong?,
) : Deserializer.EntryIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
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

        return when {
            buffer.nextValueIsIndefiniteBreak -> false.also { Cbor.Encoding.IndefiniteBreak.decode(buffer) }
            buffer.nextValueIsNull -> false.also { Cbor.Encoding.Null.decode(buffer) }
            else -> true.also {
                peekMajor(buffer).also {
                    check(it == Major.STRING) { "Expected string type for CBOR map key, got $it" }
                }
            }
        }
    }

    override fun key(): String = deserializeString().also { currentLength += 1uL }

    override fun nextHasValue(): Boolean = !buffer.nextValueIsNull
}
