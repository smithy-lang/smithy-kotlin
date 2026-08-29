/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.schema.ListSchema
import aws.smithy.kotlin.runtime.serde.schema.MapSchema
import aws.smithy.kotlin.runtime.serde.schema.MemberSchema
import aws.smithy.kotlin.runtime.serde.schema.PreludeSchemas
import aws.smithy.kotlin.runtime.serde.schema.ShapeType
import aws.smithy.kotlin.runtime.serde.schema.SimpleSchema
import aws.smithy.kotlin.runtime.serde.schema.StructureSchema
import aws.smithy.kotlin.runtime.serde.schema.UnionSchema
import aws.smithy.kotlin.runtime.serde.schema.shapeId
import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.SparseTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormatTrait
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Per-type coverage for [JsonShapeSerializer] / [JsonShapeDeserializer]. Each type builds only the schema it
 * needs. Simple types are exercised at the top level, where no enclosing struct means the serializer emits a
 * bare JSON value (no property name) — the cleanest way to assert one write in isolation.
 */
class JsonShapeSerdeTest {
    private fun ByteArray.str() = decodeToString()
    private fun ser() = JsonShapeSerializer(JsonCodecSettings())
    private fun de(json: String) = JsonShapeDeserializer(json.encodeToByteArray(), JsonCodecSettings())

    // ── boolean ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBoolean() {
        assertEquals("true", ser().apply { writeBoolean(PreludeSchemas.Boolean, true) }.flush().str())
        assertEquals("false", ser().apply { writeBoolean(PreludeSchemas.Boolean, false) }.flush().str())
        assertEquals(true, de("true").readBoolean(PreludeSchemas.Boolean))
        assertEquals(false, de("false").readBoolean(PreludeSchemas.Boolean))
    }

    // ── integral numbers ────────────────────────────────────────────────────────────────────────

    @Test
    fun testByte() {
        assertEquals("42", ser().apply { writeByte(PreludeSchemas.Byte, 42) }.flush().str())
        assertEquals("-7", ser().apply { writeByte(PreludeSchemas.Byte, (-7).toByte()) }.flush().str())
        assertEquals(42.toByte(), de("42").readByte(PreludeSchemas.Byte))
        assertEquals((-7).toByte(), de("-7").readByte(PreludeSchemas.Byte))
    }

    @Test
    fun testShort() {
        assertEquals("30000", ser().apply { writeShort(PreludeSchemas.Short, 30000) }.flush().str())
        assertEquals(30000.toShort(), de("30000").readShort(PreludeSchemas.Short))
        assertEquals((-30000).toShort(), de("-30000").readShort(PreludeSchemas.Short))
    }

    @Test
    fun testInt() {
        assertEquals("2147483647", ser().apply { writeInt(PreludeSchemas.Integer, Int.MAX_VALUE) }.flush().str())
        assertEquals("-2147483648", ser().apply { writeInt(PreludeSchemas.Integer, Int.MIN_VALUE) }.flush().str())
        assertEquals(Int.MAX_VALUE, de("2147483647").readInt(PreludeSchemas.Integer))
        assertEquals(Int.MIN_VALUE, de("-2147483648").readInt(PreludeSchemas.Integer))
    }

    @Test
    fun testLong() {
        assertEquals("9223372036854775807", ser().apply { writeLong(PreludeSchemas.Long, Long.MAX_VALUE) }.flush().str())
        assertEquals(Long.MAX_VALUE, de("9223372036854775807").readLong(PreludeSchemas.Long))
        assertEquals(Long.MIN_VALUE, de("-9223372036854775808").readLong(PreludeSchemas.Long))
    }

    // ── floating point (incl. non-finite → stringified, matching legacy encoder) ─────────────────

    @Test
    fun testFloatFinite() {
        assertEquals("1.5", ser().apply { writeFloat(PreludeSchemas.Float, 1.5f) }.flush().str())
        assertEquals(1.5f, de("1.5").readFloat(PreludeSchemas.Float))
    }

    @Test
    fun testFloatNonFinite() {
        assertEquals("\"NaN\"", ser().apply { writeFloat(PreludeSchemas.Float, Float.NaN) }.flush().str())
        assertEquals("\"Infinity\"", ser().apply { writeFloat(PreludeSchemas.Float, Float.POSITIVE_INFINITY) }.flush().str())
        assertEquals("\"-Infinity\"", ser().apply { writeFloat(PreludeSchemas.Float, Float.NEGATIVE_INFINITY) }.flush().str())
        assertTrue(de("\"NaN\"").readFloat(PreludeSchemas.Float).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, de("\"Infinity\"").readFloat(PreludeSchemas.Float))
    }

    @Test
    fun testDoubleFinite() {
        assertEquals("3.14159", ser().apply { writeDouble(PreludeSchemas.Double, 3.14159) }.flush().str())
        assertEquals(3.14159, de("3.14159").readDouble(PreludeSchemas.Double))
    }

    @Test
    fun testDoubleNonFinite() {
        assertEquals("\"NaN\"", ser().apply { writeDouble(PreludeSchemas.Double, Double.NaN) }.flush().str())
        assertTrue(de("\"NaN\"").readDouble(PreludeSchemas.Double).isNaN())
        assertEquals(Double.NEGATIVE_INFINITY, de("\"-Infinity\"").readDouble(PreludeSchemas.Double))
    }

    // ── big numbers ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBigInteger() {
        val big = BigInteger("123456789012345678901234567890")
        assertEquals("123456789012345678901234567890", ser().apply { writeBigInteger(PreludeSchemas.BigInteger, big) }.flush().str())
        assertEquals(big, de("123456789012345678901234567890").readBigInteger(PreludeSchemas.BigInteger))
    }

    @Test
    fun testBigDecimal() {
        val big = BigDecimal("3.141592653589793238462643383279")
        assertEquals("3.141592653589793238462643383279", ser().apply { writeBigDecimal(PreludeSchemas.BigDecimal, big) }.flush().str())
        assertEquals(big, de("3.141592653589793238462643383279").readBigDecimal(PreludeSchemas.BigDecimal))
    }

    // ── string (incl. escaping + coercion of non-string tokens on read) ──────────────────────────

    @Test
    fun testString() {
        assertEquals("\"hello\"", ser().apply { writeString(PreludeSchemas.String, "hello") }.flush().str())
        assertEquals("hello", de("\"hello\"").readString(PreludeSchemas.String))
    }

    @Test
    fun testStringEscaping() {
        val ser = ser()
        ser.writeString(PreludeSchemas.String, "a\"b\\c\nd\te")
        val json = ser.flush().str()
        assertEquals("""a"b\c${'\n'}d${'\t'}e""", de(json).readString(PreludeSchemas.String))
    }

    @Test
    fun testStringEmptyAndUnicode() {
        assertEquals("", de(ser().apply { writeString(PreludeSchemas.String, "") }.flush().str()).readString(PreludeSchemas.String))
        assertEquals("日本語 🦜", de(ser().apply { writeString(PreludeSchemas.String, "日本語 🦜") }.flush().str()).readString(PreludeSchemas.String))
    }

    // ── blob (base64) ────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBlob() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val json = ser().apply { writeBlob(PreludeSchemas.Blob, bytes) }.flush().str()
        assertEquals("\"AQIDBAU=\"", json) // base64 of [1,2,3,4,5]
        assertEquals(bytes.toList(), de(json).readBlob(PreludeSchemas.Blob).toList())
    }

    @Test
    fun testBlobEmpty() {
        val json = ser().apply { writeBlob(PreludeSchemas.Blob, ByteArray(0)) }.flush().str()
        assertEquals("\"\"", json)
        assertEquals(0, de(json).readBlob(PreludeSchemas.Blob).size)
    }

    // ── timestamp (all three formats) ────────────────────────────────────────────────────────────

    private fun timestampMember(format: TimestampFormat): MemberSchema = StructureSchema(shapeId("test#TsHolder")) {
        member("ts", PreludeSchemas.Timestamp, TimestampFormatTrait(format))
    }.member("ts")!!

    @Test
    fun testTimestampEpochSeconds() {
        val schema = timestampMember(TimestampFormat.EPOCH_SECONDS)
        val instant = Instant.fromEpochSeconds(1515531081)
        // top-level: bare value, epoch-seconds is a raw number
        assertEquals("1515531081", ser().apply { writeTimestamp(schema, instant) }.flush().str())
        assertEquals(instant, de("1515531081").readTimestamp(schema))
        // epoch-seconds also accepted as a quoted string on read
        assertEquals(instant, de("\"1515531081\"").readTimestamp(schema))
    }

    @Test
    fun testTimestampDateTime() {
        val schema = timestampMember(TimestampFormat.ISO_8601)
        val instant = Instant.fromEpochSeconds(1515531081)
        val json = ser().apply { writeTimestamp(schema, instant) }.flush().str()
        assertEquals("\"2018-01-09T20:51:21Z\"", json)
        assertEquals(instant, de(json).readTimestamp(schema))
    }

    @Test
    fun testTimestampHttpDate() {
        val schema = timestampMember(TimestampFormat.RFC_5322)
        val instant = Instant.fromEpochSeconds(1515531081)
        val json = ser().apply { writeTimestamp(schema, instant) }.flush().str()
        assertEquals("\"Tue, 09 Jan 2018 20:51:21 GMT\"", json)
        assertEquals(instant, de(json).readTimestamp(schema))
    }

    @Test
    fun testTimestampDefaultFormatFromSettings() {
        // no @timestampFormat trait → settings.defaultTimestampFormat applies
        val instant = Instant.fromEpochSeconds(1515531081)
        val epochSer = JsonShapeSerializer(JsonCodecSettings(defaultTimestampFormat = TimestampFormat.EPOCH_SECONDS))
        assertEquals("1515531081", epochSer.apply { writeTimestamp(PreludeSchemas.Timestamp, instant) }.flush().str())

        val dtSer = JsonShapeSerializer(JsonCodecSettings(defaultTimestampFormat = TimestampFormat.ISO_8601))
        assertEquals("\"2018-01-09T20:51:21Z\"", dtSer.apply { writeTimestamp(PreludeSchemas.Timestamp, instant) }.flush().str())
    }

    private fun eventTimeShape(format: TimestampFormat) = SimpleSchema(shapeId("test#EventTime"), ShapeType.TIMESTAMP, TimestampFormatTrait(format))

    @Test
    fun testTimestampFormatFromTargetShape() {
        // `@timestampFormat` is a shape-level trait, so a member that declares none inherits it from its target
        val schema = StructureSchema(shapeId("test#TsHolder")) {
            member("ts", eventTimeShape(TimestampFormat.ISO_8601))
        }.member("ts")!!
        val instant = Instant.fromEpochSeconds(1515531081)
        val json = ser().apply { writeTimestamp(schema, instant) }.flush().str()
        assertEquals("\"2018-01-09T20:51:21Z\"", json)
        assertEquals(instant, de(json).readTimestamp(schema))
    }

    @Test
    fun testTimestampFormatOnMemberWinsOverTargetShape() {
        val schema = StructureSchema(shapeId("test#TsHolder")) {
            member("ts", eventTimeShape(TimestampFormat.ISO_8601), TimestampFormatTrait(TimestampFormat.EPOCH_SECONDS))
        }.member("ts")!!
        val instant = Instant.fromEpochSeconds(1515531081)
        assertEquals("1515531081", ser().apply { writeTimestamp(schema, instant) }.flush().str())
        assertEquals(instant, de("1515531081").readTimestamp(schema))
    }

    // ── document (every variant + nesting) ───────────────────────────────────────────────────────

    @Test
    fun testDocumentScalars() {
        assertEquals("42", ser().apply { writeDocument(PreludeSchemas.Document, Document(42)) }.flush().str())
        assertEquals("\"hi\"", ser().apply { writeDocument(PreludeSchemas.Document, Document("hi")) }.flush().str())
        assertEquals("true", ser().apply { writeDocument(PreludeSchemas.Document, Document(true)) }.flush().str())
        assertEquals("null", ser().apply { writeDocument(PreludeSchemas.Document, null) }.flush().str())
    }

    @Test
    fun testDocumentNested() {
        // whole numbers round-trip through JSON as Long (no Int/Long distinction on the wire)
        val doc = Document(
            mapOf(
                "n" to Document(1L),
                "list" to Document(listOf(Document("a"), null, Document(true))),
                "obj" to Document(mapOf("k" to Document("v"))),
            ),
        )
        val json = ser().apply { writeDocument(PreludeSchemas.Document, doc) }.flush().str()
        assertEquals("""{"n":1,"list":["a",null,true],"obj":{"k":"v"}}""", json)
        assertEquals(doc, de(json).readDocument(PreludeSchemas.Document))
    }

    @Test
    fun testDocumentRoundTripPreservesTypes() {
        val doc = Document(listOf(Document(1L), Document(2.5), Document("x"), Document(false)))
        val json = ser().apply { writeDocument(PreludeSchemas.Document, doc) }.flush().str()
        assertEquals(doc, de(json).readDocument(PreludeSchemas.Document))
    }

    // ── list ──────────────────────────────────────────────────────────────────────────────────

    private val stringList = ListSchema(shapeId("test#StringList")) { element(PreludeSchemas.String) }
    private val stringListElem: MemberSchema get() = stringList.element

    @Test
    fun testListOfStrings() {
        val ser = ser()
        ser.writeList(stringList, 3) {
            writeString(stringListElem, "a")
            writeString(stringListElem, "b")
            writeString(stringListElem, "c")
        }
        assertEquals("""["a","b","c"]""", ser.flush().str())

        val out = mutableListOf<String>()
        de("""["a","b","c"]""").readList(stringList, out) { s, d -> s.add(d.readString(stringListElem)) }
        assertEquals(listOf("a", "b", "c"), out)
    }

    @Test
    fun testEmptyList() {
        assertEquals("[]", ser().apply { writeList(stringList, 0) {} }.flush().str())
        val out = mutableListOf<String>()
        de("[]").readList(stringList, out) { s, d -> s.add(d.readString(stringListElem)) }
        assertTrue(out.isEmpty())
    }

    @Test
    fun testSparseList() {
        // sparse list: null elements preserved on the wire
        val sparse = ListSchema(shapeId("test#SparseList")) {
            trait(SparseTrait)
            element(PreludeSchemas.String)
        }
        val elem = sparse.element
        val ser = ser()
        ser.writeList(sparse, 3) {
            writeString(elem, "a")
            writeNull(elem)
            writeString(elem, "c")
        }
        assertEquals("""["a",null,"c"]""", ser.flush().str())

        val out = mutableListOf<String?>()
        de("""["a",null,"c"]""").readList(sparse, out) { s, d ->
            if (d.isNull()) s.add(null) else s.add(d.readString(elem))
        }
        assertEquals(listOf("a", null, "c"), out)
    }

    @Test
    fun testNestedList() {
        val outer = ListSchema(shapeId("test#Matrix")) {
            element(ListSchema(shapeId("test#Row")) { element(PreludeSchemas.Integer) })
        }
        val row = outer.element.target as ListSchema
        val cell = row.element
        val ser = ser()
        ser.writeList(outer, 2) {
            writeList(row, 2) {
                writeInt(cell, 1)
                writeInt(cell, 2)
            }
            writeList(row, 1) { writeInt(cell, 3) }
        }
        assertEquals("[[1,2],[3]]", ser.flush().str())
    }

    // ── map ───────────────────────────────────────────────────────────────────────────────────

    private val intMap = MapSchema(shapeId("test#IntMap")) {
        key(PreludeSchemas.String)
        value(PreludeSchemas.Integer)
    }
    private val intMapValue: MemberSchema get() = intMap.value

    @Test
    fun testMapOfInts() {
        val ser = ser()
        ser.writeMap(intMap, 2) {
            entry(intMap.key, "a") { writeInt(intMapValue, 1) }
            entry(intMap.key, "b") { writeInt(intMapValue, 2) }
        }
        assertEquals("""{"a":1,"b":2}""", ser.flush().str())

        val out = linkedMapOf<String, Int>()
        de("""{"a":1,"b":2}""").readMap(intMap, out) { s, k, d -> s[k] = d.readInt(intMapValue) }
        assertEquals(mapOf("a" to 1, "b" to 2), out)
    }

    @Test
    fun testEmptyMap() {
        assertEquals("{}", ser().apply { writeMap(intMap, 0) {} }.flush().str())
    }

    @Test
    fun testMapOfStructs() {
        // map value is itself a struct — the value must NOT emit a spurious property name
        val point = StructureSchema(shapeId("test#Point")) {
            member("x", PreludeSchemas.Integer)
            member("y", PreludeSchemas.Integer)
        }
        val px = point.member("x")!!
        val py = point.member("y")!!
        val map = MapSchema(shapeId("test#PointMap")) {
            key(PreludeSchemas.String)
            value(point)
        }
        val ser = ser()
        ser.writeMap(map, 1) {
            entry(map.key, "origin") {
                writeStruct(map.value) {
                    writeInt(px, 0)
                    writeInt(py, 0)
                }
            }
        }
        assertEquals("""{"origin":{"x":0,"y":0}}""", ser.flush().str())
    }

    // ── struct (jsonName on/off, unknown skip, null skip, nested) ────────────────────────────────

    private fun personSchema() = StructureSchema(shapeId("test#Person")) {
        member("name", PreludeSchemas.String, JsonNameTrait("full_name"))
        member("age", PreludeSchemas.Integer)
    }

    @Test
    fun testStructIgnoresJsonNameByDefault() {
        val schema = personSchema()
        val name = schema.member("name")!!
        val age = schema.member("age")!!
        val ser = ser()
        ser.writeStruct(schema) {
            writeString(name, "Ada")
            writeInt(age, 36)
        }
        assertEquals("""{"name":"Ada","age":36}""", ser.flush().str())
    }

    @Test
    fun testStructHonorsJsonNameWhenEnabled() {
        val schema = personSchema()
        val name = schema.member("name")!!
        val ser = JsonShapeSerializer(JsonCodecSettings(useJsonName = true))
        ser.writeStruct(schema) { writeString(name, "Ada") }
        assertEquals("""{"full_name":"Ada"}""", ser.flush().str())

        // and the deserializer resolves by @jsonName too
        val values = mutableMapOf<String, String>()
        JsonShapeDeserializer("""{"full_name":"Ada"}""".encodeToByteArray(), JsonCodecSettings(useJsonName = true))
            .readStruct(schema, values) { s, m, d -> if (m.memberName == "name") s[m.memberName] = d.readString(m) }
        assertEquals(mapOf("name" to "Ada"), values)
    }

    @Test
    fun testStructSkipsUnknownMembers() {
        val schema = personSchema()
        val seen = mutableMapOf<String, Any>()
        de("""{"name":"Ada","extra":{"deep":[1,2]},"age":36}""").readStruct(schema, seen) { s, m, d ->
            when (m.memberName) {
                "name" -> s["name"] = d.readString(m)
                "age" -> s["age"] = d.readInt(m)
            }
        }
        val expected: Map<String, Any> = mapOf("name" to "Ada", "age" to 36)
        assertEquals(expected, seen)
    }

    @Test
    fun testStructSkipsExplicitNulls() {
        val schema = personSchema()
        val seen = mutableMapOf<String, Any>()
        de("""{"name":null,"age":36}""").readStruct(schema, seen) { s, m, d ->
            when (m.memberName) {
                "name" -> s["name"] = d.readString(m)
                "age" -> s["age"] = d.readInt(m)
            }
        }
        val expected: Map<String, Any> = mapOf("age" to 36)
        assertEquals(expected, seen) // name skipped, never surfaced to consumer
    }

    @Test
    fun testStructOfNullTokenIsNoOp() {
        val schema = personSchema()
        val seen = mutableListOf<String>()
        de("null").readStruct(schema, seen) { s, m, _ -> s.add(m.memberName) }
        assertTrue(seen.isEmpty())
    }

    // ── union (variant dispatch: exactly one member present) ─────────────────────────────────────

    @Test
    fun testUnion() {
        val union = UnionSchema(shapeId("test#Shape")) {
            member("circle", PreludeSchemas.Double)
            member("square", PreludeSchemas.Integer)
        }
        val circle = union.member("circle")!!
        // serialize the active variant only
        val ser = ser()
        ser.writeStruct(union) { writeDouble(circle, 2.5) }
        assertEquals("""{"circle":2.5}""", ser.flush().str())

        // deserialize dispatches to the one present member
        val result = mutableMapOf<String, Any>()
        de("""{"square":9}""").readStruct(union, result) { s, m, d ->
            when (m.memberName) {
                "circle" -> s["circle"] = d.readDouble(m)
                "square" -> s["square"] = d.readInt(m)
            }
        }
        val expected: Map<String, Any> = mapOf("square" to 9)
        assertEquals(expected, result)
    }

    // ── round trips across a mixed struct ────────────────────────────────────────────────────────

    @Test
    fun testMixedStructRoundTrip() {
        val schema = StructureSchema(shapeId("test#Mixed")) {
            member("s", PreludeSchemas.String)
            member("i", PreludeSchemas.Integer)
            member("b", PreludeSchemas.Boolean)
            member("tags", ListSchema(shapeId("test#Tags")) { element(PreludeSchemas.String) })
        }
        val s = schema.member("s")!!
        val i = schema.member("i")!!
        val b = schema.member("b")!!
        val tags = schema.member("tags")!!
        val tagElem = (tags.target as ListSchema).element

        val ser = ser()
        ser.writeStruct(schema) {
            writeString(s, "hi")
            writeInt(i, -5)
            writeBoolean(b, true)
            writeList(tags, 2) {
                writeString(tagElem, "x")
                writeString(tagElem, "y")
            }
        }
        val json = ser.flush().str()
        assertEquals("""{"s":"hi","i":-5,"b":true,"tags":["x","y"]}""", json)

        val out = mutableMapOf<String, Any?>()
        de(json).readStruct(schema, out) { st, m, d ->
            when (m.memberName) {
                "s" -> st["s"] = d.readString(m)
                "i" -> st["i"] = d.readInt(m)
                "b" -> st["b"] = d.readBoolean(m)
                "tags" -> {
                    val list = mutableListOf<String>()
                    d.readList(m, list) { l, e -> l.add(e.readString(tagElem)) }
                    st["tags"] = list
                }
            }
        }
        val expected: Map<String, Any?> = mapOf("s" to "hi", "i" to -5, "b" to true, "tags" to listOf("x", "y"))
        assertEquals(expected, out)
    }

    // ── isNull ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun testIsNull() {
        assertTrue(de("null").isNull())
        // isNull consumes the null; a non-null token is left in place and reported false
        val d = de("\"x\"")
        assertEquals(false, d.isNull())
        assertEquals("x", d.readString(PreludeSchemas.String))
    }

    @Test
    fun testReadDocumentNullReturnsNull() {
        assertNull(de("null").readDocument(PreludeSchemas.Document))
    }

    // ── flush ─────────────────────────────────────────────────────────────────────────────────
}
