/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.formurl

import software.aws.clientrt.io.SdkBuffer
import software.aws.clientrt.io.bytes
import software.aws.clientrt.io.write
import software.aws.clientrt.serde.*

fun FormUrlSerializer(): Serializer = FormUrlSerializer(SdkBuffer(256))

private class FormUrlSerializer(
    val buffer: SdkBuffer,
    val prefixFn: PrefixFn? = null
) : Serializer {

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer =
        FormUrlStructSerializer(this, prefixFn)

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer =
        FormUrlListSerializer(this, descriptor)

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        TODO("Not yet implemented")
    }

    override fun toByteArray(): ByteArray = buffer.bytes()

    private fun write(block: SdkBuffer.() -> Unit) {
        buffer.apply(block)
    }

    private fun write(value: String) = write { write(value) }

    override fun serializeBoolean(value: Boolean) = write("$value")
    override fun serializeByte(value: Byte) = write { commonWriteNumber(value) }
    override fun serializeChar(value: Char) = write(value.toString())
    override fun serializeShort(value: Short) = write { commonWriteNumber(value) }
    override fun serializeInt(value: Int) = write { commonWriteNumber(value) }
    override fun serializeLong(value: Long) = write { commonWriteNumber(value) }
    override fun serializeFloat(value: Float) = write { commonWriteNumber(value) }
    override fun serializeDouble(value: Double) = write { commonWriteNumber(value) }
    override fun serializeString(value: String) = write(value)

    override fun serializeRaw(value: String) = write(value)

    override fun serializeSdkSerializable(value: SdkSerializable) {
        value.serialize(this)
    }

    override fun serializeNull() {
        // null values are not supported
    }
}

private class FormUrlStructSerializer(
    private val parent: FormUrlSerializer,
    // field prefix generator function (e.g. nested structures, list elements, etc)
    private val prefixFn: PrefixFn? = null
) : StructSerializer, PrimitiveSerializer by parent {
    private val buffer
        get() = parent.buffer

    private fun writeField(descriptor: SdkFieldDescriptor, block: () -> Unit) {
        if (buffer.writePosition> 0) {
            buffer.write("&")
        }
        val prefix = prefixFn?.invoke()
        prefix?.let { buffer.write(it) }
        buffer.write(descriptor.serialName)
        buffer.write("=")
        block()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) = writeField(descriptor) {
        serializeBoolean(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) = writeField(descriptor) {
        serializeByte(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) = writeField(descriptor) {
        serializeShort(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) = writeField(descriptor) {
        serializeChar(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int) = writeField(descriptor) {
        serializeInt(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) = writeField(descriptor) {
        serializeLong(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) = writeField(descriptor) {
        serializeFloat(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) = writeField(descriptor) {
        serializeDouble(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) = writeField(descriptor) {
        serializeString(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        val nestedPrefix = "${descriptor.serialName}."
        value.serialize(FormUrlSerializer(buffer) { nestedPrefix })
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        // FIXME - do we even use this function in any of the formats? It seems like we go through `field(.., SdkSerializable)` ??
        TODO("Not yet implemented")
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        FormUrlListSerializer(parent, descriptor).apply(block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun rawField(descriptor: SdkFieldDescriptor, value: String) = writeField(descriptor) {
        serializeRaw(value)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        // null not supported
    }

    override fun endStruct() {
        // no terminating tokens for a struct
    }
}

private class FormUrlListSerializer(
    parent: FormUrlSerializer,
    private val descriptor: SdkFieldDescriptor
) : ListSerializer {
    private val buffer = parent.buffer
    private var cnt = 0

    private fun prefix(): String {
        return "${descriptor.serialName}.member.$cnt"
    }

    private fun writePrefixed(block: SdkBuffer.() -> Unit) {
        cnt++
        if (buffer.writePosition > 0) buffer.write("&")
        buffer.write(prefix())
        buffer.write("=")
        buffer.apply(block)
    }

    override fun endList() {}
    override fun serializeBoolean(value: Boolean) = writePrefixed { write("$value") }
    override fun serializeChar(value: Char) = writePrefixed { write(value.toString()) }
    override fun serializeByte(value: Byte) = writePrefixed { commonWriteNumber(value) }
    override fun serializeShort(value: Short) = writePrefixed { commonWriteNumber(value) }
    override fun serializeInt(value: Int) = writePrefixed { commonWriteNumber(value) }
    override fun serializeLong(value: Long) = writePrefixed { commonWriteNumber(value) }
    override fun serializeFloat(value: Float) = writePrefixed { commonWriteNumber(value) }
    override fun serializeDouble(value: Double) = writePrefixed { commonWriteNumber(value) }
    override fun serializeString(value: String) = writePrefixed { write(value) }
    override fun serializeRaw(value: String) = writePrefixed { write(value) }

    override fun serializeSdkSerializable(value: SdkSerializable) {
        val nestedFn: PrefixFn = {
            cnt++
            prefix() + "."
        }

        value.serialize(FormUrlSerializer(buffer, nestedFn))
    }

    override fun serializeNull() {}
}

private fun SdkBuffer.commonWriteNumber(value: Number): Unit = write(value.toString())

private typealias PrefixFn = () -> String
