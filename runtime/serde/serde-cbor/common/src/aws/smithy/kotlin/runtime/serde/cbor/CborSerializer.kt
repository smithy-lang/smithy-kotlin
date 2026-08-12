/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.serde.cbor.encoding.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.math.absoluteValue

private val BIG_INTEGER_ZERO = BigInteger("0")

@InternalApi
public class CborSerializer :
    Serializer,
    ListSerializer,
    MapSerializer,
    StructSerializer {
    private val buffer = SdkBuffer()

    // Cache the fully-encoded CBOR bytes (length header + UTF-8) for each struct field name. A single
    // CborSerializer encodes an entire object graph on one thread, and the same schema field names recur
    // on every struct occurrence, so caching avoids re-encoding the same name (UTF-8 conversion + length
    // header) on every field(...) call.
    private val fieldNameBytes = HashMap<String, ByteArray>()

    private fun writeFieldName(name: String) {
        val bytes = fieldNameBytes.getOrPut(name) {
            val utf8 = name.encodeToByteArray()
            val tmp = SdkBuffer()
            tmp.writeArgument(Major.STRING, utf8.size.toULong())
            tmp.write(utf8)
            tmp.readByteArray()
        }
        buffer.write(bytes)
    }

    public fun toHttpBody(): HttpBody = buffer.readByteArray().toHttpBody()

    override fun toByteArray(): ByteArray = buffer.readByteArray()

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        // TODO Encoding indefinite maps comes with some performance overhead, see if we can refactor mapEntry interface to
        // pass additional information such as the map length. That way we can serialize a definite-length map.
        // Write the indefinite-map head byte directly instead of allocating an IndefiniteMap value.
        buffer.writeByte(encodeMajorMinor(Major.MAP, Minor.INDEFINITE))
        return this
    }

    override fun endMap(): Unit = buffer.write(IndefiniteBreak)

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        // TODO Encoding indefinite lists comes with some performance overhead, see if we can refactor listEntry interface to
        // pass additional information such as the list length. That way we can serialize a definite-length list.
        // Write the indefinite-list head byte directly instead of allocating an IndefiniteList value.
        buffer.writeByte(encodeMajorMinor(Major.LIST, Minor.INDEFINITE))
        return this
    }

    override fun endList(): Unit = buffer.write(IndefiniteBreak)

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        beginMap(descriptor)
        return this
    }

    override fun endStruct(): Unit = endMap()

    override fun serializeBoolean(value: Boolean): Unit =
        buffer.writeByte(encodeMajorMinor(Major.TYPE_7, if (value) Minor.TRUE else Minor.FALSE))

    // Write integers directly to the buffer instead of allocating a UInt/NegInt value. This matches the
    // exact bytes UInt/NegInt.encode would emit (NegInt encodes -1 - value, i.e. abs(value) - 1).
    private fun serializeNumber(value: Long): Unit = if (value < 0) {
        buffer.writeArgument(Major.NEG_INT, value.absoluteValue.toULong() - 1u)
    } else {
        buffer.writeArgument(Major.U_INT, value.toULong())
    }
    override fun serializeByte(value: Byte): Unit = serializeNumber(value.toLong())
    override fun serializeShort(value: Short): Unit = serializeNumber(value.toLong())
    override fun serializeInt(value: Int): Unit = serializeNumber(value.toLong())
    override fun serializeLong(value: Long): Unit = serializeNumber(value)

    override fun serializeFloat(value: Float): Unit = buffer.write(Float32(value))

    override fun serializeDouble(value: Double): Unit = buffer.write(Float64(value))

    override fun serializeBigInteger(value: BigInteger) {
        // Check the sign via comparison instead of value.toString().startsWith("-"), which allocates the
        // full decimal string of a potentially huge integer just to read its sign.
        if (value < BIG_INTEGER_ZERO) {
            buffer.write(NegBigNum(value))
        } else {
            buffer.write(BigNum(value))
        }
    }

    override fun serializeBigDecimal(value: BigDecimal): Unit = buffer.write(DecimalFraction(value))

    override fun serializeChar(value: Char): Unit = buffer.write(TextString(value.toString()))

    override fun serializeString(value: String) {
        // Inline TextString.encode to avoid allocating a wrapper value per string (the hottest serialize
        // path: every field name and string value). Encode UTF-8 once and use its byte length.
        val bytes = value.encodeToByteArray()
        buffer.writeArgument(Major.STRING, bytes.size.toULong())
        buffer.write(bytes)
    }

    // Note: CBOR does not use [TimestampFormat]
    override fun serializeInstant(value: Instant, format: TimestampFormat): Unit = serializeInstant(value)
    public fun serializeInstant(value: Instant): Unit = buffer.write(Timestamp(value))

    override fun serializeByteArray(value: ByteArray): Unit = buffer.write(ByteString(value))

    override fun serializeSdkSerializable(value: SdkSerializable): Unit = value.serialize(this)

    override fun serializeNull(): Unit = buffer.write(Null)

    override fun serializeDocument(value: Document?): Unit = throw SerializationException("Document is not a supported CBOR type")

    private inline fun <T> serializeEntry(key: String, value: T?, serializeValue: (T) -> Unit) {
        serializeString(key)
        value?.let(serializeValue) ?: serializeNull()
    }
    override fun entry(key: String, value: Boolean?): Unit = serializeEntry(key, value, ::serializeBoolean)
    override fun entry(key: String, value: Byte?): Unit = serializeEntry(key, value, ::serializeByte)
    override fun entry(key: String, value: Short?): Unit = serializeEntry(key, value, ::serializeShort)
    override fun entry(key: String, value: Char?): Unit = serializeEntry(key, value, ::serializeChar)
    override fun entry(key: String, value: Int?): Unit = serializeEntry(key, value, ::serializeInt)
    override fun entry(key: String, value: Long?): Unit = serializeEntry(key, value, ::serializeLong)
    override fun entry(key: String, value: Float?): Unit = serializeEntry(key, value, ::serializeFloat)
    override fun entry(key: String, value: Double?): Unit = serializeEntry(key, value, ::serializeDouble)
    override fun entry(key: String, value: String?): Unit = serializeEntry(key, value, ::serializeString)
    override fun entry(key: String, value: ByteArray?): Unit = serializeEntry(key, value, ::serializeByteArray)
    override fun entry(key: String, value: Document?): Unit = throw SerializationException("Document is not a supported CBOR type.")

    override fun entry(key: String, value: Instant?, format: TimestampFormat) {
        serializeString(key)
        value?.let {
            serializeInstant(it, format)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: SdkSerializable?) {
        serializeString(key)
        value?.let {
            serializeSdkSerializable(value)
        } ?: serializeNull()
    }

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        serializeString(key)
        beginList(listDescriptor)
        block()
        endList()
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        serializeString(key)
        beginMap(mapDescriptor)
        block()
        endMap()
    }

    // field(...) values are non-null, so we can write the (cached) field name directly and serialize the
    // value, skipping entry()'s dead null-check and its re-encoding of the field name via serializeString.
    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) { writeFieldName(descriptor.serialName); serializeBoolean(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Byte) { writeFieldName(descriptor.serialName); serializeByte(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Short) { writeFieldName(descriptor.serialName); serializeShort(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Char) { writeFieldName(descriptor.serialName); serializeChar(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Int) { writeFieldName(descriptor.serialName); serializeInt(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Long) { writeFieldName(descriptor.serialName); serializeLong(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Float) { writeFieldName(descriptor.serialName); serializeFloat(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Double) { writeFieldName(descriptor.serialName); serializeDouble(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: String) { writeFieldName(descriptor.serialName); serializeString(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat) { writeFieldName(descriptor.serialName); serializeInstant(value, format) }
    override fun field(descriptor: SdkFieldDescriptor, value: Document?): Unit = throw SerializationException("Document is not a supported CBOR type.")
    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) { writeFieldName(descriptor.serialName); serializeSdkSerializable(value) }
    override fun field(descriptor: SdkFieldDescriptor, value: ByteArray) { writeFieldName(descriptor.serialName); serializeByteArray(value) }

    override fun field(descriptor: SdkFieldDescriptor, value: BigInteger) {
        writeFieldName(descriptor.serialName)
        serializeBigInteger(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigDecimal) {
        writeFieldName(descriptor.serialName)
        serializeBigDecimal(value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        writeFieldName(descriptor.serialName)
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        writeFieldName(descriptor.serialName)
        serializeList(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        writeFieldName(descriptor.serialName)
        serializeMap(descriptor, block)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        writeFieldName(descriptor.serialName)
        serializeNull()
    }
}
