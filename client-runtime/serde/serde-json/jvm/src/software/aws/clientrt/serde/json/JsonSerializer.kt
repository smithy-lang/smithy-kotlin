/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.*

class JsonSerializer : Serializer, ListSerializer, MapSerializer, StructSerializer {

    private val jsonWriter = JsonStreamWriter()

    fun getBytes(): ByteArray? {
        return jsonWriter.bytes
    }

    override fun beginStruct(): StructSerializer {
        jsonWriter.beginObject()
        return this
    }

    override fun beginList(): ListSerializer {
        jsonWriter.beginArray()
        return this
    }

    override fun beginMap(): MapSerializer {
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
        jsonWriter.writeName(descriptor.name)
        value.serialize(this)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int) {
        jsonWriter.writeName(descriptor.name)
        serializeInt(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) {
        jsonWriter.writeName(descriptor.name)
        serializeLong(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) {
        jsonWriter.writeName(descriptor.name)
        serializeFloat(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) {
        jsonWriter.writeName(descriptor.name)
        serializeString(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) {
        jsonWriter.writeName(descriptor.name)
        serializeDouble(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) {
        jsonWriter.writeName(descriptor.name)
        serializeBoolean(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) {
        jsonWriter.writeName(descriptor.name)
        serializeByte(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) {
        jsonWriter.writeName(descriptor.name)
        serializeShort(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) {
        jsonWriter.writeName(descriptor.name)
        serializeChar(value)
    }

    override fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.name)
        serializeStruct(block)
    }

    override fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.name)
        serializeList(block)
    }

    override fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit) {
        jsonWriter.writeName(descriptor.name)
        serializeMap(block)
    }

    override fun entry(key: String, value: Int) {
        jsonWriter.writeName(key)
        serializeInt(value)
    }

    override fun entry(key: String, value: Long) {
        jsonWriter.writeName(key)
        serializeLong(value)
    }

    override fun entry(key: String, value: Float) {
        jsonWriter.writeName(key)
        serializeFloat(value)
    }

    override fun entry(key: String, value: String) {
        jsonWriter.writeName(key)
        serializeString(value)
    }

    override fun entry(key: String, value: SdkSerializable) {
        jsonWriter.writeName(key)
        value.serialize(this)
    }

    override fun entry(key: String, value: Double) {
        jsonWriter.writeName(key)
        serializeDouble(value)
    }

    override fun entry(key: String, value: Boolean) {
        jsonWriter.writeName(key)
        serializeBoolean(value)
    }

    override fun entry(key: String, value: Byte) {
        jsonWriter.writeName(key)
        serializeByte(value)
    }

    override fun entry(key: String, value: Short) {
        jsonWriter.writeName(key)
        serializeShort(value)
    }

    override fun entry(key: String, value: Char) {
        jsonWriter.writeName(key)
        serializeChar(value)
    }

    override fun serializeNull(descriptor: SdkFieldDescriptor) {
        jsonWriter.writeName(descriptor.name)
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
}
