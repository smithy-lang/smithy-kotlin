/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.math.absoluteValue

@InternalApi
public class CborSerializer :
    Serializer,
    ListSerializer,
    MapSerializer,
    StructSerializer {
    private val buffer = SdkBuffer()

    override fun toByteArray(): ByteArray = buffer.readByteArray()

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        buffer.write(Cbor.Encoding.IndefiniteMap())
        return this
    }

    override fun endMap(): Unit = buffer.write(Cbor.Encoding.IndefiniteBreak())

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        buffer.write(Cbor.Encoding.IndefiniteList())
        return this
    }

    override fun endList(): Unit = buffer.write(Cbor.Encoding.IndefiniteBreak())

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        beginMap(descriptor)
        return this
    }

    override fun endStruct(): Unit = endMap()

    override fun serializeBoolean(value: Boolean): Unit = buffer.write(Cbor.Encoding.Boolean(value))

    private inline fun <reified T : Number> serializeNumber(value: T): Unit = buffer.write(
        if (value.toLong() < 0) {
            Cbor.Encoding.NegInt(value.toLong().absoluteValue.toULong())
        } else {
            Cbor.Encoding.UInt(value.toLong().toULong())
        },
    )
    override fun serializeByte(value: Byte): Unit = serializeNumber(value)
    override fun serializeShort(value: Short): Unit = serializeNumber(value)
    override fun serializeInt(value: Int): Unit = serializeNumber(value)
    override fun serializeLong(value: Long): Unit = serializeNumber(value)

    override fun serializeFloat(value: Float): Unit = buffer.write(Cbor.Encoding.Float32(value))

    override fun serializeDouble(value: Double): Unit = buffer.write(Cbor.Encoding.Float64(value))

    override fun serializeBigInteger(value: BigInteger) {
        if (value.toString().startsWith("-")) {
            buffer.write(Cbor.Encoding.NegBigNum(value))
        } else {
            buffer.write(Cbor.Encoding.BigNum(value))
        }
    }

    override fun serializeBigDecimal(value: BigDecimal): Unit = buffer.write(Cbor.Encoding.DecimalFraction(value))

    override fun serializeChar(value: Char): Unit = buffer.write(Cbor.Encoding.String(value.toString()))

    override fun serializeString(value: String): Unit = buffer.write(Cbor.Encoding.String(value))

    // Note: CBOR does not use [TimestampFormat]
    override fun serializeInstant(value: Instant, format: TimestampFormat): Unit = serializeInstant(value)
    public fun serializeInstant(value: Instant): Unit = buffer.write(Cbor.Encoding.Timestamp(value))

    override fun serializeByteArray(value: ByteArray): Unit = buffer.write(Cbor.Encoding.ByteString(value))

    override fun serializeSdkSerializable(value: SdkSerializable): Unit = value.serialize(this)

    override fun serializeNull(): Unit = buffer.write(Cbor.Encoding.Null())

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

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Byte): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Short): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Char): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Int): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Long): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Float): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Double): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: String): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat): Unit = entry(descriptor.serialName, value, format)
    override fun field(descriptor: SdkFieldDescriptor, value: Document?): Unit = throw SerializationException("Document is not a supported CBOR type.")
    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable): Unit = entry(descriptor.serialName, value)
    override fun field(descriptor: SdkFieldDescriptor, value: ByteArray): Unit = entry(descriptor.serialName, value)

    override fun field(descriptor: SdkFieldDescriptor, value: BigInteger) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeBigInteger(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigDecimal) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeBigDecimal(value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeList(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeMap(descriptor, block)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName))
        serializeNull()
    }
}
