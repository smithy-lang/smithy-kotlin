/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.serde

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.schema.ListSchema
import aws.smithy.kotlin.runtime.serde.schema.MemberSchema
import aws.smithy.kotlin.runtime.serde.schema.PreludeSchemas
import aws.smithy.kotlin.runtime.serde.schema.Schema
import aws.smithy.kotlin.runtime.serde.schema.StructureSchema
import aws.smithy.kotlin.runtime.serde.schema.shapeId
import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// ── a minimal generated-style shape, mirroring the design doc's Bird example ──────────────────────

private class Bird private constructor(builder: Builder) : SerializableStruct {
    val name: String? = builder.name
    val colors: List<String>? = builder.colors

    companion object {
        val SCHEMA: StructureSchema = StructureSchema(shapeId("com.example#Bird")) {
            member("name", PreludeSchemas.String, JsonNameTrait("bird_name"))
            member("colors", ListSchema(shapeId("com.example#ColorList")) { element(PreludeSchemas.String) })
        }
        val NAME: MemberSchema = SCHEMA.member("name")!!
        val COLORS: MemberSchema = SCHEMA.member("colors")!!
        val COLORS_ELEMENT: MemberSchema = (COLORS.target as ListSchema).element
    }

    override fun serialize(serializer: ShapeSerializer<*>): Unit = serializer.writeStruct(SCHEMA) {
        name?.let { writeString(NAME, it) }
        colors?.let { list ->
            writeList(COLORS, list.size) {
                list.forEach { c -> writeString(COLORS_ELEMENT, c) }
            }
        }
    }

    class Builder : ShapeBuilder<Bird> {
        var name: String? = null
        var colors: List<String>? = null

        override fun deserialize(deserializer: ShapeDeserializer) {
            deserializer.readStruct(SCHEMA, this) { b, member, d ->
                when (member.memberName) {
                    "name" -> b.name = d.readString(member)
                    "colors" -> {
                        val out = ArrayList<String>()
                        d.readList(member, out) { list, e -> list.add(e.readString(COLORS_ELEMENT)) }
                        b.colors = out
                    }
                }
            }
        }

        override fun build(): Bird = Bird(this)
    }
}

class ShapeSerdeTest {
    @Test
    fun testRoundTripsThroughDocumentBackedSerde() {
        val bird = Bird.Builder().apply {
            name = "Iago"
            colors = listOf("red", "green")
        }.build()

        val ser = DocumentShapeSerializer()
        bird.serialize(ser)
        val doc = ser.flush()

        // serialized form keyed by MODEL member names (wire-name resolution is a codec concern)
        assertEquals(
            Document.Map(
                mapOf(
                    "name" to Document.String("Iago"),
                    "colors" to Document.List(listOf(Document.String("red"), Document.String("green"))),
                ),
            ),
            doc,
        )

        val roundTripped = Bird.Builder().apply { deserialize(DocumentShapeDeserializer(doc)) }.build()
        assertEquals("Iago", roundTripped.name)
        assertEquals(listOf("red", "green"), roundTripped.colors)
    }

    @Test
    fun testStructConsumerReceivesEachMember() {
        val doc = Document.Map(mapOf("name" to Document.String("Toco")))
        val seen = mutableListOf<String>()
        DocumentShapeDeserializer(doc).readStruct(Bird.SCHEMA, seen) { state, member, _ ->
            state.add(member.memberName)
        }
        assertEquals(listOf("name"), seen)
    }
}

// ── in-memory Document-backed serializer/deserializer (test harness only) ─────────────────────────

private class DocumentShapeSerializer :
    ShapeSerializer<Document>,
    StructSerializer,
    ListSerializer,
    MapSerializer {
    private var result: Document? = null

    override fun flush(): Document = requireNotNull(result) { "nothing was written" }

    private fun set(value: Document?) {
        result = value
    }

    override fun writeBoolean(schema: Schema, value: Boolean) = set(Document.Boolean(value))
    override fun writeByte(schema: Schema, value: Byte) = set(Document.Number(value))
    override fun writeShort(schema: Schema, value: Short) = set(Document.Number(value))
    override fun writeInt(schema: Schema, value: Int) = set(Document.Number(value))
    override fun writeLong(schema: Schema, value: Long) = set(Document.Number(value))
    override fun writeFloat(schema: Schema, value: Float) = set(Document.Number(value))
    override fun writeDouble(schema: Schema, value: Double) = set(Document.Number(value))
    override fun writeBigInteger(schema: Schema, value: BigInteger) = set(Document.String(value.toString()))
    override fun writeBigDecimal(schema: Schema, value: BigDecimal) = set(Document.String(value.toString()))
    override fun writeString(schema: Schema, value: String) = set(Document.String(value))
    override fun writeBlob(schema: Schema, value: ByteArray) = set(Document.String(value.decodeToString()))
    override fun writeTimestamp(schema: Schema, value: Instant) = set(Document.String(value.toString()))
    override fun writeDocument(schema: Schema, value: Document?) = set(value)
    override fun writeNull(schema: Schema) = set(null)

    override fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        val child = MemberCapture(entries)
        child.block()
        set(Document.Map(entries))
    }

    override fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit) {
        val items = ArrayList<Document?>(size)
        val child = ListCapture(items)
        child.block()
        set(Document.List(items))
    }

    override fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MapCapture(entries).block()
        set(Document.Map(entries))
    }

    // MapSerializer for the top-level (unused here but required by the type)
    override fun entry(keySchema: MemberSchema, key: String, block: ValueSerializer.() -> Unit) {
        error("top-level entry not supported in test harness")
    }
}

// captures each struct member write into a name->Document map, keyed by the member's model name
private class MemberCapture(private val out: MutableMap<String, Document?>) :
    StructSerializer,
    MapSerializer {
    private fun put(schema: Schema, value: Document?) {
        out[(schema as MemberSchema).memberName] = value
    }
    override fun writeBoolean(schema: Schema, value: Boolean) = put(schema, Document.Boolean(value))
    override fun writeByte(schema: Schema, value: Byte) = put(schema, Document.Number(value))
    override fun writeShort(schema: Schema, value: Short) = put(schema, Document.Number(value))
    override fun writeInt(schema: Schema, value: Int) = put(schema, Document.Number(value))
    override fun writeLong(schema: Schema, value: Long) = put(schema, Document.Number(value))
    override fun writeFloat(schema: Schema, value: Float) = put(schema, Document.Number(value))
    override fun writeDouble(schema: Schema, value: Double) = put(schema, Document.Number(value))
    override fun writeBigInteger(schema: Schema, value: BigInteger) = put(schema, Document.String(value.toString()))
    override fun writeBigDecimal(schema: Schema, value: BigDecimal) = put(schema, Document.String(value.toString()))
    override fun writeString(schema: Schema, value: String) = put(schema, Document.String(value))
    override fun writeBlob(schema: Schema, value: ByteArray) = put(schema, Document.String(value.decodeToString()))
    override fun writeTimestamp(schema: Schema, value: Instant) = put(schema, Document.String(value.toString()))
    override fun writeDocument(schema: Schema, value: Document?) = put(schema, value)
    override fun writeNull(schema: Schema) = put(schema, null)

    override fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MemberCapture(entries).block()
        put(schema, Document.Map(entries))
    }
    override fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit) {
        val items = ArrayList<Document?>(size)
        ListCapture(items).block()
        put(schema, Document.List(items))
    }
    override fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MapCapture(entries).block()
        put(schema, Document.Map(entries))
    }
    override fun entry(keySchema: MemberSchema, key: String, block: ValueSerializer.() -> Unit) {
        val slot = SingleValueCapture()
        slot.block()
        out[key] = slot.value
    }
}

// captures list element writes into a Document list
private class ListCapture(private val out: MutableList<Document?>) : ListSerializer {
    private fun add(value: Document?) {
        out.add(value)
    }
    override fun writeBoolean(schema: Schema, value: Boolean) = add(Document.Boolean(value))
    override fun writeByte(schema: Schema, value: Byte) = add(Document.Number(value))
    override fun writeShort(schema: Schema, value: Short) = add(Document.Number(value))
    override fun writeInt(schema: Schema, value: Int) = add(Document.Number(value))
    override fun writeLong(schema: Schema, value: Long) = add(Document.Number(value))
    override fun writeFloat(schema: Schema, value: Float) = add(Document.Number(value))
    override fun writeDouble(schema: Schema, value: Double) = add(Document.Number(value))
    override fun writeBigInteger(schema: Schema, value: BigInteger) = add(Document.String(value.toString()))
    override fun writeBigDecimal(schema: Schema, value: BigDecimal) = add(Document.String(value.toString()))
    override fun writeString(schema: Schema, value: String) = add(Document.String(value))
    override fun writeBlob(schema: Schema, value: ByteArray) = add(Document.String(value.decodeToString()))
    override fun writeTimestamp(schema: Schema, value: Instant) = add(Document.String(value.toString()))
    override fun writeDocument(schema: Schema, value: Document?) = add(value)
    override fun writeNull(schema: Schema) = add(null)
    override fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MemberCapture(entries).block()
        add(Document.Map(entries))
    }
    override fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit) {
        val items = ArrayList<Document?>(size)
        ListCapture(items).block()
        add(Document.List(items))
    }
    override fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MapCapture(entries).block()
        add(Document.Map(entries))
    }
}

private class MapCapture(private val out: MutableMap<String, Document?>) : MapSerializer {
    override fun entry(keySchema: MemberSchema, key: String, block: ValueSerializer.() -> Unit) {
        val slot = SingleValueCapture()
        slot.block()
        out[key] = slot.value
    }
}

// captures a single value write (for map entry values)
private class SingleValueCapture : ValueSerializer {
    var value: Document? = null
    private fun set(v: Document?) {
        value = v
    }
    override fun writeBoolean(schema: Schema, value: Boolean) = set(Document.Boolean(value))
    override fun writeByte(schema: Schema, value: Byte) = set(Document.Number(value))
    override fun writeShort(schema: Schema, value: Short) = set(Document.Number(value))
    override fun writeInt(schema: Schema, value: Int) = set(Document.Number(value))
    override fun writeLong(schema: Schema, value: Long) = set(Document.Number(value))
    override fun writeFloat(schema: Schema, value: Float) = set(Document.Number(value))
    override fun writeDouble(schema: Schema, value: Double) = set(Document.Number(value))
    override fun writeBigInteger(schema: Schema, value: BigInteger) = set(Document.String(value.toString()))
    override fun writeBigDecimal(schema: Schema, value: BigDecimal) = set(Document.String(value.toString()))
    override fun writeString(schema: Schema, value: String) = set(Document.String(value))
    override fun writeBlob(schema: Schema, value: ByteArray) = set(Document.String(value.decodeToString()))
    override fun writeTimestamp(schema: Schema, value: Instant) = set(Document.String(value.toString()))
    override fun writeDocument(schema: Schema, value: Document?) = set(value)
    override fun writeNull(schema: Schema) = set(null)
    override fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MemberCapture(entries).block()
        set(Document.Map(entries))
    }
    override fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit) {
        val items = ArrayList<Document?>(size)
        ListCapture(items).block()
        set(Document.List(items))
    }
    override fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit) {
        val entries = linkedMapOf<String, Document?>()
        MapCapture(entries).block()
        set(Document.Map(entries))
    }
}

// reads from an in-memory Document
private class DocumentShapeDeserializer(private val node: Document?) : ShapeDeserializer {
    private fun str() = (node as Document.String).value
    private fun num() = (node as Document.Number).value

    override fun readBoolean(schema: Schema): Boolean = (node as Document.Boolean).value
    override fun readByte(schema: Schema): Byte = num().toByte()
    override fun readShort(schema: Schema): Short = num().toShort()
    override fun readInt(schema: Schema): Int = num().toInt()
    override fun readLong(schema: Schema): Long = num().toLong()
    override fun readFloat(schema: Schema): Float = num().toFloat()
    override fun readDouble(schema: Schema): Double = num().toDouble()
    override fun readBigInteger(schema: Schema): BigInteger = BigInteger(str())
    override fun readBigDecimal(schema: Schema): BigDecimal = BigDecimal(str())
    override fun readString(schema: Schema): String = str()
    override fun readBlob(schema: Schema): ByteArray = str().encodeToByteArray()
    override fun readTimestamp(schema: Schema): Instant = Instant.fromIso8601(str())
    override fun readDocument(schema: Schema): Document? = node
    override fun isNull(): Boolean = node == null

    override fun <T> readStruct(schema: Schema, state: T, consumer: StructConsumer<T>) {
        val map = (node as Document.Map).value
        val struct = schema as StructureSchema
        for ((name, value) in map) {
            val member = struct.member(name) ?: continue
            consumer.accept(state, member, DocumentShapeDeserializer(value))
        }
    }

    override fun <T> readList(schema: Schema, state: T, consumer: ListConsumer<T>) {
        for (item in (node as Document.List).value) {
            consumer.accept(state, DocumentShapeDeserializer(item))
        }
    }

    override fun <T> readMap(schema: Schema, state: T, consumer: MapConsumer<T>) {
        for ((key, value) in (node as Document.Map).value) {
            consumer.accept(state, key, DocumentShapeDeserializer(value))
        }
    }
}
