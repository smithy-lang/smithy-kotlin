/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.schema.ListSchema
import aws.smithy.kotlin.runtime.serde.schema.PreludeSchemas
import aws.smithy.kotlin.runtime.serde.schema.ShapeType
import aws.smithy.kotlin.runtime.serde.schema.SimpleSchema
import aws.smithy.kotlin.runtime.serde.schema.StructureSchema
import aws.smithy.kotlin.runtime.serde.schema.serde.SerializableStruct
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeBuilder
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeDeserializer
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.StructSerializer
import aws.smithy.kotlin.runtime.serde.schema.serde.deserialize
import aws.smithy.kotlin.runtime.serde.schema.serde.serialize
import aws.smithy.kotlin.runtime.serde.schema.shapeId
import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormatTrait
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The codec is the entry point for open-world serde: a shape hands itself to whatever serializer it is given,
 * so the same `Bird` serializes differently under different codec settings with no change to the shape.
 */
class JsonCodecTest {
    // com.example#Bird { name: String @jsonName("bird_name"), colors: ColorList }
    private class Bird(val name: String?, val colors: List<String>?) : SerializableStruct {
        companion object {
            val SCHEMA: StructureSchema = StructureSchema(shapeId("com.example#Bird")) {
                member("name", PreludeSchemas.String, JsonNameTrait("bird_name"))
                member(
                    "colors",
                    ListSchema(shapeId("com.example#ColorList")) { element(PreludeSchemas.String) },
                )
            }
            val NAME = SCHEMA.member("name")!!
            val COLORS = SCHEMA.member("colors")!!
            val COLORS_ELEMENT = (COLORS.target as ListSchema).element
        }

        override fun serialize(serializer: ShapeSerializer<*>) = serializer.writeStruct(SCHEMA) { serializeMembers(this) }

        override fun serializeMembers(dest: StructSerializer) {
            name?.let { dest.writeString(NAME, it) }
            colors?.let { list ->
                dest.writeList(COLORS, list.size) {
                    list.forEach { writeString(COLORS_ELEMENT, it) }
                }
            }
        }

        class Builder : ShapeBuilder<Bird> {
            var name: String? = null
            var colors: List<String>? = null

            override fun deserialize(deserializer: ShapeDeserializer) {
                deserializer.readStruct(SCHEMA, this) { builder, member, de ->
                    when (member.memberName) {
                        "name" -> builder.name = de.readString(member)
                        "colors" -> builder.colors = mutableListOf<String>().also { out ->
                            de.readList(COLORS, out) { list, el -> list.add(el.readString(COLORS_ELEMENT)) }
                        }
                    }
                }
            }

            override fun build(): Bird = Bird(name, colors)
        }
    }

    // com.example#Sighting { at: EventTime }, where EventTime is `timestamp @timestampFormat("date-time")` — the
    // trait sits on the target shape, which is where Smithy allows it, and the member declares nothing.
    private class Sighting(val at: Instant?) : SerializableStruct {
        companion object {
            val SCHEMA: StructureSchema = StructureSchema(shapeId("com.example#Sighting")) {
                member(
                    "at",
                    SimpleSchema(
                        shapeId("com.example#EventTime"),
                        ShapeType.TIMESTAMP,
                        TimestampFormatTrait(TimestampFormat.ISO_8601),
                    ),
                )
            }
            val AT = SCHEMA.member("at")!!
        }

        override fun serialize(serializer: ShapeSerializer<*>) = serializer.writeStruct(SCHEMA) { serializeMembers(this) }

        override fun serializeMembers(dest: StructSerializer) {
            at?.let { dest.writeTimestamp(AT, it) }
        }

        class Builder : ShapeBuilder<Sighting> {
            var at: Instant? = null

            override fun deserialize(deserializer: ShapeDeserializer) {
                deserializer.readStruct(SCHEMA, this) { builder, member, de ->
                    if (member.memberName == "at") builder.at = de.readTimestamp(member)
                }
            }

            override fun build(): Sighting = Sighting(at)
        }
    }

    private val bird = Bird("robin", listOf("red", "brown"))

    @Test
    fun testSerializeAndDeserializeThroughTheCodec() {
        val codec = JsonCodec()

        val bytes = codec.serialize(bird)
        assertEquals("""{"name":"robin","colors":["red","brown"]}""", bytes.decodeToString())

        val roundTripped = codec.deserialize(bytes, Bird.Builder())
        assertEquals(bird.name, roundTripped.name)
        assertEquals(bird.colors, roundTripped.colors)
    }

    @Test
    fun testTheSameShapeSerializesDifferentlyPerCodecSettings() {
        // restJson1 honors @jsonName, awsJson1_1 does not — same shape, same generated walk, different bytes
        val restJson = JsonCodec(JsonCodecSettings(useJsonName = true))
        val awsJson = JsonCodec(JsonCodecSettings(useJsonName = false))

        assertEquals(
            """{"bird_name":"robin","colors":["red","brown"]}""",
            restJson.serialize(bird).decodeToString(),
        )
        assertEquals(
            """{"name":"robin","colors":["red","brown"]}""",
            awsJson.serialize(bird).decodeToString(),
        )

        // and each reads back what it writes
        assertEquals("robin", restJson.deserialize(restJson.serialize(bird), Bird.Builder()).name)
        assertEquals("robin", awsJson.deserialize(awsJson.serialize(bird), Bird.Builder()).name)
    }

    @Test
    fun testCodecExposesItsSettings() {
        val codec = JsonCodec(JsonCodecSettings(defaultTimestampFormat = TimestampFormat.ISO_8601))
        assertEquals(TimestampFormat.ISO_8601, codec.settings.defaultTimestampFormat)
        assertEquals(true, JsonCodec(JsonCodecSettings(useJsonName = true)).settings.useJsonName)
    }

    @Test
    fun testUnsetMembersAreOmitted() {
        val codec = JsonCodec()
        assertEquals("""{"name":"robin"}""", codec.serialize(Bird("robin", null)).decodeToString())
        assertEquals("{}", codec.serialize(Bird(null, null)).decodeToString())
    }

    @Test
    fun testTimestampFormatOnTargetShapeOverridesTheCodecDefault() {
        // the codec defaults to epoch seconds, so honoring the target's date-time is what keeps the bytes correct
        val codec = JsonCodec(JsonCodecSettings(useJsonName = true))
        val sighting = Sighting(Instant.fromEpochSeconds(1515531081))

        val bytes = codec.serialize(sighting)
        assertEquals("""{"at":"2018-01-09T20:51:21Z"}""", bytes.decodeToString())
        assertEquals(sighting.at, codec.deserialize(bytes, Sighting.Builder()).at)
    }
}
