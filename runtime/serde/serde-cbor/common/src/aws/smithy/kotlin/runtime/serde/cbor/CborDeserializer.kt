package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.time.Instant

public class CborDeserializer(payload: ByteArray) : Deserializer {
    private val buffer = SdkBuffer().apply { write(payload) }

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        val major = peekMajor(buffer)
        check(major == Major.MAP) { "Expected major ${Major.MAP} for structure, got $major." }

        deserializeArgument(buffer) // toss head + any following bytes which encode length

        return CborFieldIterator(buffer, descriptor)
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        val major = peekMajor(buffer)
        check(major == Major.LIST) { "Expected major ${Major.LIST} for CBOR list, got $major." }

        deserializeArgument(buffer) // toss head + any following bytes which encode length

        return CborElementIterator(buffer)
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        val major = peekMajor(buffer)
        check(major == Major.MAP) { "Expected major ${Major.MAP} for CBOR map, got $major." }

        deserializeArgument(buffer) // toss head + any following bytes which encode length

        return CborEntryIterator(buffer)
    }
}

private class CborPrimitiveDeserializer(private val buffer: SdkBuffer) : PrimitiveDeserializer {
    override fun deserializeByte(): Byte = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toByte()
        Major.NEG_INT -> (0 - Cbor.Encoding.NegInt.decode(buffer).value.toByte()).toByte()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR byte, got $major.")
    }

    override fun deserializeInt(): Int = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toInt()
        Major.NEG_INT -> 0 - Cbor.Encoding.NegInt.decode(buffer).value.toInt()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR integer, got $major.")
    }

    override fun deserializeShort(): Short = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toShort()
        Major.NEG_INT -> (0 - Cbor.Encoding.NegInt.decode(buffer).value.toShort()).toShort()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR short, got $major.")
    }

    override fun deserializeLong(): Long = when (val major = peekMajor(buffer)) {
        Major.U_INT -> Cbor.Encoding.UInt.decode(buffer).value.toLong()
        Major.NEG_INT -> 0 - Cbor.Encoding.NegInt.decode(buffer).value.toLong()
        else -> throw DeserializationException("Expected ${Major.U_INT} or ${Major.NEG_INT} for CBOR short, got $major.")
    }

    override fun deserializeFloat(): Float = Cbor.Encoding.Float32.decode(buffer).value

    override fun deserializeDouble(): Double = Cbor.Encoding.Float64.decode(buffer).value

    override fun deserializeBigInteger(): BigInteger = when(val tagId = peekTag(buffer).id.toUInt()) {
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

    private fun deserializeBlob(): ByteArray = Cbor.Encoding.ByteString.decode(buffer).value

    private fun deserializeTimestamp(): Instant = Cbor.Encoding.Timestamp.decode(buffer).value
}

/**
 * Element iterator used for deserializing lists
 */
private class CborElementIterator(
    val buffer: SdkBuffer,
) : Deserializer.ElementIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    override fun hasNextElement(): Boolean {
        val value = decodeNextValue(buffer.peek().buffer)
        return (value !is Cbor.Encoding.IndefiniteBreak)
    }

    override fun nextHasValue(): Boolean {
        val value = decodeNextValue(buffer.peek().buffer)
        return (value !is Cbor.Encoding.Null)
    }
}

/**
 * Field iterator used for deserializing structures
 */
private class CborFieldIterator(
    val buffer: SdkBuffer,
    val descriptor: SdkObjectDescriptor,
) : Deserializer.FieldIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    override fun findNextFieldIndex(): Int? {
        val nextFieldName = Cbor.Encoding.String.decode(buffer.peek().buffer).value
        return descriptor
            .fields
            .firstOrNull { it.serialName.equals(nextFieldName, ignoreCase = true) }
            ?.index
    }

    override fun skipValue() { decodeNextValue(buffer) }
}

/**
 * Entry iterator used for deserializing maps
 */
private class CborEntryIterator(val buffer: SdkBuffer) : Deserializer.EntryIterator, PrimitiveDeserializer by CborPrimitiveDeserializer(buffer) {
    override fun hasNextEntry(): Boolean {
        val nextKey = decodeNextValue(buffer.peek().buffer)
        return nextKey !is Cbor.Encoding.IndefiniteBreak && nextKey !is Cbor.Encoding.Null
    }

    override fun key(): String = Cbor.Encoding.String.decode(buffer).value

    override fun nextHasValue(): Boolean {
        val value = decodeNextValue(buffer.peek().buffer)
        return value !is Cbor.Encoding.Null
    }
}