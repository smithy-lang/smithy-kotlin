/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*

class JsonSerializer : Serializer, ListSerializer, MapSerializer, StructSerializer {

    private val jsonWriter = jsonStreamWriter()

    override fun toByteArray(): ByteArray {
        return jsonWriter.bytes ?: throw SerializationException("Serializer payload is empty")
    }

    override fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer {
        jsonWriter.beginObject()
        return this
    }

    override fun beginList(descriptor: SdkFieldDescriptor): ListSerializer {
        jsonWriter.beginArray()
        return this
    }

    override fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer {
        jsonWriter.beginObject()
        return this
    }

    override fun endStruct() {
        jsonWriter.endObject()
    }

    override fun endList() {
        jsonWriter.endArray()
    }

    override fun endMap() {
        jsonWriter.endObject()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable) {
        jsonWriter.writeName(descriptor.serialName)
        value.serialize(this)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeInt(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeLong(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeFloat(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeString(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeDouble(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeBoolean(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeByte(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeShort(value) else serializeNull()
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char?) {
        jsonWriter.writeName(descriptor.serialName)
        if (value != null) serializeChar(value) else serializeNull()
    }

    override fun rawField(descriptor: SdkFieldDescriptor, value: String) {
        jsonWriter.writeName(descriptor.serialName)
        serializeRaw(value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.serialName)
        serializeStruct(descriptor, block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.serialName)
        serializeList(descriptor, block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.serialName)
        serializeMap(descriptor, block)
    }

    override fun entry(key: String, value: Int?) {
        jsonWriter.writeName(key)
        if (value != null) serializeInt(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Long?) {
        jsonWriter.writeName(key)
        if (value != null) serializeLong(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Float?) {
        jsonWriter.writeName(key)
        if (value != null) serializeFloat(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: String?) {
        jsonWriter.writeName(key)
        if (value != null) serializeString(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: SdkSerializable?) {
        jsonWriter.writeName(key)
        value?.serialize(this) ?: jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Double?) {
        jsonWriter.writeName(key)
        if (value != null) serializeDouble(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Boolean?) {
        jsonWriter.writeName(key)
        if (value != null) serializeBoolean(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Byte?) {
        jsonWriter.writeName(key)
        if (value != null) serializeByte(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Short?) {
        jsonWriter.writeName(key)
        if (value != null) serializeShort(value) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Char?) {
        jsonWriter.writeName(key)
        if (value != null) serializeChar(value) else jsonWriter.writeNull()
    }

    override fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        jsonWriter.writeName(key)
        beginList(listDescriptor)
        block.invoke(this)
        endList()
    }

    override fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        jsonWriter.writeName(key)
        beginMap(mapDescriptor)
        block.invoke(this)
        endMap()
    }

    override fun rawEntry(key: String, value: String) {
        jsonWriter.writeName(key)
        serializeRaw(value)
    }

    override fun serializeNull() {
        jsonWriter.writeNull()
    }

    override fun serializeBoolean(value: Boolean) {
        jsonWriter.writeValue(value)
    }

    override fun serializeByte(value: Byte) {
        jsonWriter.writeValue(value)
    }

    override fun serializeShort(value: Short) {
        jsonWriter.writeValue(value)
    }

    override fun serializeChar(value: Char) {
        jsonWriter.writeValue(value.toString())
    }

    override fun serializeInt(value: Int) {
        jsonWriter.writeValue(value)
    }

    override fun serializeLong(value: Long) {
        jsonWriter.writeValue(value)
    }

    override fun serializeFloat(value: Float) {
        jsonWriter.writeValue(value)
    }

    override fun serializeDouble(value: Double) {
        jsonWriter.writeValue(value)
    }

    override fun serializeString(value: String) {
        jsonWriter.writeValue(value)
    }

    override fun serializeSdkSerializable(value: SdkSerializable) {
        value.serialize(this)
    }

    override fun serializeRaw(value: String) {
        jsonWriter.writeRawValue(value)
    }
}
