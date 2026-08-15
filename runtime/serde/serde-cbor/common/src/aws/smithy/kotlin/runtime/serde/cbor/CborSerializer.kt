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
import aws.smithy.kotlin.runtime.serde.cbor.encoding.BigNum
import aws.smithy.kotlin.runtime.serde.cbor.encoding.ByteString
import aws.smithy.kotlin.runtime.serde.cbor.encoding.DecimalFraction
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Float32
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Float64
import aws.smithy.kotlin.runtime.serde.cbor.encoding.IndefiniteBreak
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Minor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.NegBigNum
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Null
import aws.smithy.kotlin.runtime.serde.cbor.encoding.TextString
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Timestamp
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

    // Tracks, for each currently-open container, whether it was written with an indefinite length and therefore must
    // be terminated with a "break" byte. Definite-length containers push `false` and write no break on close.
    private val indefiniteContainers = ArrayDeque<Boolean>()

    public fun toHttpBody(): HttpBody = buffer.readByteArray().toHttpBody()

    override fun toByteArray(): ByteArray = buffer.readByteArray()

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        buffer.writeByte(encodeMajorMinor(Major.MAP, Minor.INDEFINITE))
        indefiniteContainers.addLast(true)
        return this
    }

    override fun beginMap(descriptor: SdkFieldDescriptor, size: Int): MapSerializer {
        buffer.writeArgument(Major.MAP, size.toULong())
        indefiniteContainers.addLast(false)
        return this
    }

    override fun endMap() {
        if (indefiniteContainers.removeLast()) buffer.write(IndefiniteBreak)
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        buffer.writeByte(encodeMajorMinor(Major.LIST, Minor.INDEFINITE))
        indefiniteContainers.addLast(true)
        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor, size: Int): ListSerializer {
        buffer.writeArgument(Major.LIST, size.toULong())
        indefiniteContainers.addLast(false)
        return this
    }

    override fun endList() {
        if (indefiniteContainers.removeLast()) buffer.write(IndefiniteBreak)
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        beginMap(descriptor)
        return this
    }

    override fun endStruct(): Unit = endMap()

    override fun serializeBoolean(value: Boolean): Unit = buffer.writeByte(encodeMajorMinor(Major.TYPE_7, if (value) Minor.TRUE else Minor.FALSE))

    private inline fun <reified T : Number> serializeNumber(value: T) {
        val longValue = value.toLong()
        if (longValue < 0) {
            buffer.writeArgument(Major.NEG_INT, longValue.absoluteValue.toULong() - 1u)
        } else {
            buffer.writeArgument(Major.U_INT, longValue.toULong())
        }
    }
    override fun serializeByte(value: Byte): Unit = serializeNumber(value)
    override fun serializeShort(value: Short): Unit = serializeNumber(value)
    override fun serializeInt(value: Int): Unit = serializeNumber(value)
    override fun serializeLong(value: Long): Unit = serializeNumber(value)

    override fun serializeFloat(value: Float): Unit = buffer.write(Float32(value))

    override fun serializeDouble(value: Double): Unit = buffer.write(Float64(value))

    override fun serializeBigInteger(value: BigInteger) {
        if (value.toString().startsWith("-")) {
            buffer.write(NegBigNum(value))
        } else {
            buffer.write(BigNum(value))
        }
    }

    override fun serializeBigDecimal(value: BigDecimal): Unit = buffer.write(DecimalFraction(value))

    override fun serializeChar(value: Char): Unit = buffer.write(TextString(value.toString()))

    override fun serializeString(value: String) {
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

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, size: Int, block: ListSerializer.() -> Unit) {
        serializeString(key)
        beginList(listDescriptor, size)
        block()
        endList()
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        serializeString(key)
        beginMap(mapDescriptor)
        block()
        endMap()
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, size: Int, block: MapSerializer.() -> Unit) {
        serializeString(key)
        beginMap(mapDescriptor, size)
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
        buffer.write(TextString(descriptor.serialName))
        serializeBigInteger(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigDecimal) {
        buffer.write(TextString(descriptor.serialName))
        serializeBigDecimal(value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        buffer.write(TextString(descriptor.serialName))
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        buffer.write(TextString(descriptor.serialName))
        serializeList(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, size: Int, block: ListSerializer.() -> Unit) {
        buffer.write(TextString(descriptor.serialName))
        serializeList(descriptor, size, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        buffer.write(TextString(descriptor.serialName))
        serializeMap(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, size: Int, block: MapSerializer.() -> Unit) {
        buffer.write(TextString(descriptor.serialName))
        serializeMap(descriptor, size, block)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        buffer.write(TextString(descriptor.serialName))
        serializeNull()
    }
}
