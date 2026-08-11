/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.schema.MemberSchema
import aws.smithy.kotlin.runtime.serde.schema.Schema
import aws.smithy.kotlin.runtime.serde.schema.getTrait
import aws.smithy.kotlin.runtime.serde.schema.serde.ListSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.MapSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.StructSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.ValueSerializer
import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormat
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormatTrait
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat as WireTimestampFormat

public class JsonShapeSerializer(
    private val settings: JsonCodecSettings = JsonCodecSettings(),
) : ShapeSerializer<ByteArray>,
    StructSerializer,
    ListSerializer,
    MapSerializer {
    private val writer = jsonStreamWriter()

    private enum class Container { STRUCT, LIST, MAP }
    private val containers = ArrayDeque<Container>()

    override fun flush(): ByteArray = writer.bytes ?: ByteArray(0)

    // ── simple types ─────────────────────────────────────────────────────────────────────────────

    override fun writeBoolean(schema: Schema, value: Boolean) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeByte(schema: Schema, value: Byte) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeShort(schema: Schema, value: Short) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeInt(schema: Schema, value: Int) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeLong(schema: Schema, value: Long) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeFloat(schema: Schema, value: Float) {
        writeName(schema)
        if (value.isFinite()) writer.writeValue(value) else writer.writeValue(value.toString())
    }

    override fun writeDouble(schema: Schema, value: Double) {
        writeName(schema)
        if (value.isFinite()) writer.writeValue(value) else writer.writeValue(value.toString())
    }

    override fun writeBigInteger(schema: Schema, value: BigInteger) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeBigDecimal(schema: Schema, value: BigDecimal) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeString(schema: Schema, value: String) {
        writeName(schema)
        writer.writeValue(value)
    }

    override fun writeBlob(schema: Schema, value: ByteArray) {
        writeName(schema)
        writer.writeValue(value.encodeBase64String())
    }

    override fun writeTimestamp(schema: Schema, value: Instant) {
        writeName(schema)
        when (val fmt = wireTimestampFormat(schema)) {
            WireTimestampFormat.EPOCH_SECONDS -> writer.writeRawValue(value.format(fmt))
            else -> writer.writeValue(value.format(fmt))
        }
    }

    override fun writeDocument(schema: Schema, value: Document?) {
        writeName(schema)
        writeDocumentValue(value)
    }

    override fun writeNull(schema: Schema) {
        writeName(schema)
        writer.writeNull()
    }

    // ── aggregate types ──────────────────────────────────────────────────────────────────────────

    override fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit) {
        writeName(schema)
        writer.beginObject()
        containers.addLast(Container.STRUCT)
        this.block()
        containers.removeLast()
        writer.endObject()
    }

    override fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit) {
        writeName(schema)
        writer.beginArray()
        containers.addLast(Container.LIST)
        this.block()
        containers.removeLast()
        writer.endArray()
    }

    override fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit) {
        writeName(schema)
        writer.beginObject()
        containers.addLast(Container.MAP)
        this.block()
        containers.removeLast()
        writer.endObject()
    }

    // MapSerializer: a map key is always a raw wire name (never subject to @jsonName). The value written in
    // [block] must not emit a name of its own, so it runs in LIST context (name-suppressing, non-container).
    override fun entry(keySchema: MemberSchema, key: String, block: ValueSerializer.() -> Unit) {
        writer.writeName(key)
        containers.addLast(Container.LIST)
        this.block()
        containers.removeLast()
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    // Emit a JSON property name only for struct members. List elements and map values reuse a MemberSchema
    // (the collection's element/value schema), so the schema kind can't distinguish them — the enclosing
    // container does: names are emitted only directly inside a struct.
    private fun writeName(schema: Schema) {
        if (containers.lastOrNull() != Container.STRUCT) return
        if (schema !is MemberSchema) return
        val wireName = if (settings.useJsonName) {
            schema.getTrait<JsonNameTrait>(JsonNameTrait.ID)?.value ?: schema.memberName
        } else {
            schema.memberName
        }
        writer.writeName(wireName)
    }

    private fun wireTimestampFormat(schema: Schema): WireTimestampFormat {
        val schemaFormat = schema.getTrait<TimestampFormatTrait>(TimestampFormatTrait.ID)?.format
            ?: settings.defaultTimestampFormat
        return schemaFormat.toWireFormat()
    }

    private fun writeDocumentValue(value: Document?) {
        when (value) {
            null -> writer.writeNull()
            is Document.Number -> writer.writeValue(value.value)
            is Document.String -> writer.writeValue(value.value)
            is Document.Boolean -> writer.writeValue(value.value)
            is Document.List -> {
                writer.beginArray()
                value.value.forEach { writeDocumentValue(it) }
                writer.endArray()
            }
            is Document.Map -> {
                writer.beginObject()
                value.value.forEach { (k, v) ->
                    writer.writeName(k)
                    writeDocumentValue(v)
                }
                writer.endObject()
            }
        }
    }
}

internal fun TimestampFormat.toWireFormat(): WireTimestampFormat = when (this) {
    TimestampFormat.EPOCH_SECONDS -> WireTimestampFormat.EPOCH_SECONDS
    TimestampFormat.DATE_TIME -> WireTimestampFormat.ISO_8601
    TimestampFormat.HTTP_DATE -> WireTimestampFormat.RFC_5322
}
