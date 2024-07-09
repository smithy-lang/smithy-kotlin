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
import aws.smithy.kotlin.runtime.serde.cbor.encoding.String as cborString
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Boolean as cborBoolean
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
            Major.U_INT -> cast(UInt.decode(buffer).value.toLong())
            Major.NEG_INT -> cast(-NegInt.decode(buffer).value.toLong())
            else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR number, got $major.")
        }

    override fun deserializeByte(): Byte = deserializeNumber { it.toByte() }
    override fun deserializeInt(): Int = deserializeNumber { it.toInt() }
    override fun deserializeShort(): Short = deserializeNumber { it.toShort() }
    override fun deserializeLong(): Long = deserializeNumber { it.toLong() }

    private inline fun <reified T : Number> deserializeFloatingPoint(cast: (Number) -> T): T {
        val number = when (peekMinorByte(buffer)) {
            Minor.FLOAT16.value -> Float16.decode(buffer).value
            Minor.FLOAT32.value -> Float32.decode(buffer).value
            Minor.FLOAT64.value -> Float64.decode(buffer).value
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

    override fun deserializeBigInteger(): BigInteger = when (val tag = Tag.decode(buffer).value) {
        is BigNum -> tag.value
        is NegBigNum -> tag.value
        else -> throw DeserializationException("Expected tag ${TagId.BIG_NUM.value} or ${TagId.NEG_BIG_NUM.value} for CBOR bignum, got $tag")
    }

    override fun deserializeBigDecimal(): BigDecimal {
        val tag = Tag.decode(buffer)
        return (tag.value as DecimalFraction).value
    }

    override fun deserializeString(): String = cborString.decode(buffer).value

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
                currentLength += 1u
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

    override fun findNextFieldIndex(): Int? {
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
            val nextFieldName = cborString.decode(buffer).value
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
