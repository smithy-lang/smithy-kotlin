/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

@InternalApi
public class JsonSerializer :
    Serializer,
    ListSerializer,
    MapSerializer,
    StructSerializer {
    @InternalApi
    public companion object {
        private val doublesToStringify = setOf(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN)
        private val floatsToStringify = setOf(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN)
    }

    private val jsonWriter = jsonStreamWriter()

    override fun toByteArray(): ByteArray =
        jsonWriter.bytes ?: throw SerializationException("Serializer payload is empty")

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

    override fun field(descriptor: SdkFieldDescriptor, value: ByteArray) {
        jsonWriter.writeName(descriptor.serialName)
        serializeByteArray(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Int) {
        jsonWriter.writeName(descriptor.serialName)
        serializeInt(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Long) {
        jsonWriter.writeName(descriptor.serialName)
        serializeLong(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Float) {
        jsonWriter.writeName(descriptor.serialName)
        serializeFloat(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: String) {
        jsonWriter.writeName(descriptor.serialName)
        serializeString(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Double) {
        jsonWriter.writeName(descriptor.serialName)
        serializeDouble(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigInteger) {
        jsonWriter.writeName(descriptor.serialName)
        serializeBigInteger(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: BigDecimal) {
        jsonWriter.writeName(descriptor.serialName)
        serializeBigDecimal(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Boolean) {
        jsonWriter.writeName(descriptor.serialName)
        serializeBoolean(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Byte) {
        jsonWriter.writeName(descriptor.serialName)
        serializeByte(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Short) {
        jsonWriter.writeName(descriptor.serialName)
        serializeShort(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Char) {
        jsonWriter.writeName(descriptor.serialName)
        serializeChar(value)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat) {
        jsonWriter.writeName(descriptor.serialName)
        serializeInstant(value, format)
    }

    override fun field(descriptor: SdkFieldDescriptor, value: Document?) {
        jsonWriter.writeName(descriptor.serialName)
        serializeDocument(value)
    }

    override fun nullField(descriptor: SdkFieldDescriptor) {
        jsonWriter.writeName(descriptor.serialName)
        serializeNull()
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

    override fun entry(key: String, value: Instant?, format: TimestampFormat) {
        jsonWriter.writeName(key)
        if (value != null) serializeInstant(value, format) else jsonWriter.writeNull()
    }

    override fun entry(key: String, value: Document?) {
        jsonWriter.writeName(key)
        serializeDocument(value)
    }

    override fun entry(key: String, value: ByteArray?) {
        jsonWriter.writeName(key)
        if (value != null) serializeByteArray(value) else jsonWriter.writeNull()
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
        if (floatsToStringify.contains(value)) {
            jsonWriter.writeValue(value.toString())
        } else {
            jsonWriter.writeValue(value)
        }
    }

    override fun serializeDouble(value: Double) {
        if (doublesToStringify.contains(value)) {
            jsonWriter.writeValue(value.toString())
        } else {
            jsonWriter.writeValue(value)
        }
    }

    override fun serializeBigInteger(value: BigInteger) {
        jsonWriter.writeValue(value)
    }

    override fun serializeBigDecimal(value: BigDecimal) {
        jsonWriter.writeValue(value)
    }

    override fun serializeString(value: String) {
        jsonWriter.writeValue(value)
    }

    override fun serializeSdkSerializable(value: SdkSerializable) {
        value.serialize(this)
    }

    override fun serializeInstant(value: Instant, format: TimestampFormat) {
        when (format) {
            TimestampFormat.EPOCH_SECONDS -> jsonWriter.writeRawValue(value.format(format))
            TimestampFormat.ISO_8601,
            TimestampFormat.ISO_8601_CONDENSED,
            TimestampFormat.ISO_8601_CONDENSED_DATE,
            TimestampFormat.ISO_8601_FULL,
            TimestampFormat.RFC_5322,
            -> jsonWriter.writeValue(value.format(format))
        }
    }

    override fun serializeByteArray(value: ByteArray) {
        serializeString(value.encodeBase64String())
    }

    override fun serializeDocument(value: Document?) {
        when (value) {
            is Document.Number -> jsonWriter.writeValue(value.value)
            is Document.String -> jsonWriter.writeValue(value.value)
            is Document.Boolean -> jsonWriter.writeValue(value.value)
            null -> jsonWriter.writeNull()
            is Document.List -> {
                jsonWriter.beginArray()
                value.value.forEach(::serializeDocument)
                jsonWriter.endArray()
            }
            is Document.Map -> {
                jsonWriter.beginObject()
                value.value.entries.forEach {
                    jsonWriter.writeName(it.key)
                    serializeDocument(it.value)
                }
                jsonWriter.endObject()
            }
        }
    }
}
