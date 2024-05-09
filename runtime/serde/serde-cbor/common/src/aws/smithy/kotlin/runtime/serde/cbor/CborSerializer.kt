package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.time.epochMilliseconds

@InternalApi
public class CborSerializer : Serializer, ListSerializer, MapSerializer, StructSerializer {
    private val buffer = SdkBuffer()

    override fun toByteArray(): ByteArray = buffer.readByteArray()

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        buffer.write(Cbor.Encoding.IndefiniteMap().encode())
        return this
    }

    override fun endMap() {
        buffer.write(Cbor.Encoding.IndefiniteBreak().encode())
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        buffer.write(Cbor.Encoding.IndefiniteList().encode())
        return this
    }

    override fun endList() {
        buffer.write(Cbor.Encoding.IndefiniteBreak().encode())
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        beginMap(descriptor)
        return this
    }

    override fun endStruct() { endMap() }

    override fun serializeBoolean(value: Boolean) {
        buffer.write(Cbor.Encoding.Boolean(value).encode())
    }

    override fun serializeByte(value: Byte) {
        if (value < 0) {
            buffer.write(Cbor.Encoding.NegInt(value.toULong()).encode())
        } else {
            buffer.write(Cbor.Encoding.UInt(value.toULong()).encode())
        }
    }

    override fun serializeShort(value: Short) {
        if (value < 0) {
            buffer.write(Cbor.Encoding.NegInt(value.toULong()).encode())
        } else {
            buffer.write(Cbor.Encoding.UInt(value.toULong()).encode())
        }
    }

    override fun serializeChar(value: Char) {
        buffer.write(Cbor.Encoding.String(value.toString()).encode())
    }

    override fun serializeInt(value: Int) {
        if (value < 0) {
            buffer.write(Cbor.Encoding.NegInt(value.toULong()).encode())
        } else {
            buffer.write(Cbor.Encoding.UInt(value.toULong()).encode())
        }
    }

    override fun serializeLong(value: Long) {
        if (value < 0) {
            buffer.write(Cbor.Encoding.NegInt(value.toULong()).encode())
        } else {
            buffer.write(Cbor.Encoding.UInt(value.toULong()).encode())
        }
    }

    override fun serializeFloat(value: Float) {
        if (value == value.toLong().toFloat()) {
            // Floating-point numeric types MAY be serialized into non-floating-point numeric types if and only if the conversion would not cause a loss of precision.
            serializeLong(value.toLong())
        } else {
            buffer.write(Cbor.Encoding.Float32(value).encode())
        }
    }

    override fun serializeDouble(value: Double) {
        if (value == value.toLong().toDouble()) {
            // Floating-point numeric types MAY be serialized into non-floating-point numeric types if and only if the conversion would not cause a loss of precision.
            serializeLong(value.toLong())
        } else {
            buffer.write(Cbor.Encoding.Float64(value).encode())
        }
    }

    override fun serializeBigInteger(value: BigInteger) {
        if (value.toInt() >= 0) {
            Cbor.Encoding.BigNum(value).encode()
        } else {
            Cbor.Encoding.NegBigNum(value).encode()
        }
    }

    // Tagged (major 6, minor 4) array with two integers (exponent and mantissa)
    override fun serializeBigDecimal(value: BigDecimal) {
        buffer.write(Cbor.Encoding.DecimalFraction(value).encode())
    }

    override fun serializeString(value: String) {
        buffer.write(Cbor.Encoding.String(value).encode())
    }

    override fun serializeInstant(value: Instant, format: TimestampFormat) {
        buffer.write(
            Cbor.Encoding.Tag(
                1u, // Tag ID 1 indicates the wrapped value is an epoch-seconds timestamp
                Cbor.Encoding.Float64(value.epochMilliseconds / 1000.toDouble())
            ).encode()
        )
    }

    override fun serializeSdkSerializable(value: SdkSerializable) {
        value.serialize(this)
    }

    override fun serializeNull() {
        buffer.write(Cbor.Encoding.Null().encode())
    }

    override fun serializeDocument(value: Document?) { throw SerializationException("Document is not a supported CBOR type.") }

    override fun entry(key: String, value: Boolean?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeBoolean(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Byte?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeByte(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Short?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeShort(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Char?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeChar(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Int?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeInt(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Long?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeLong(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Float?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeFloat(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Double?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeDouble(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: String?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeString(it)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Instant?, format: TimestampFormat) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeInstant(it, format)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: SdkSerializable?) {
        buffer.write(Cbor.Encoding.String(key).encode())
        value?.let {
            serializeSdkSerializable(value)
        } ?: serializeNull()
    }

    override fun entry(key: String, value: Document?) { throw SerializationException("Document is not a supported CBOR type.") }

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(key).encode())
        beginList(listDescriptor)
        block()
        endList()
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(key).encode())
        beginMap(mapDescriptor)
        block()
        endMap()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigInteger) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeBigInteger(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigDecimal) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeBigDecimal(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) {
        entry(descriptor.serialName, value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat) {
        entry(descriptor.serialName, value, format)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Document?) { throw SerializationException("Document is not a supported CBOR type.") }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        entry(descriptor.serialName, value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeList(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeMap(descriptor, block)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        buffer.write(Cbor.Encoding.String(descriptor.serialName).encode())
        serializeNull()
    }
}
