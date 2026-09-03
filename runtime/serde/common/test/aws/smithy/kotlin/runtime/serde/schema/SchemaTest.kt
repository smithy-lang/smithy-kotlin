/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.SerdeTraits
import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaTest {
    // com.example#Bird { name: String @jsonName("bird_name"), colors: ColorList }
    private val colorListId = ShapeId("com.example#ColorList")
    private val colorList = ListSchema(colorListId, MemberSchema(colorListId.withMember("member"), PreludeSchemas.String))

    private val birdSchema: StructureSchema = StructureSchema(ShapeId("com.example#Bird")) {
        member("name", PreludeSchemas.String, SerdeTraits.JsonNameTrait("bird_name"))
        member(
            "colors",
            colorList,
        )
    }

    @Test
    fun testStructureShapeMetadata() {
        assertEquals(ShapeType.STRUCTURE, birdSchema.type)
        assertEquals("com.example#Bird", birdSchema.shapeId.absoluteId)
        assertEquals(2, birdSchema.members.size)
    }

    @Test
    fun testMemberLookupByName() {
        val name = assertNotNull(birdSchema.member("name"))
        assertEquals("name", name.memberName)
        assertEquals(ShapeType.MEMBER, name.type)
        assertEquals(ShapeType.STRING, name.target.type)
        assertNull(birdSchema.member("missing"))
    }

    @Test
    fun testListElementNavigation() {
        val colors = assertNotNull(birdSchema.member("colors"))
        val list = colors.target as ListSchema
        assertEquals(ShapeType.STRING, list.element.target.type)
    }

    @Test
    fun testTraitLookupById() {
        val name = assertNotNull(birdSchema.member("name"))
        assertTrue(name.hasTrait(SerdeTraits.JsonNameTrait.ID))
        val jsonName = assertNotNull(name.getTraitOrNull<SerdeTraits.JsonNameTrait>(SerdeTraits.JsonNameTrait.ID))
        assertEquals("bird_name", jsonName.value)

        assertFalse(name.hasTrait(SerdeTraits.XmlNameTrait.ID))
        assertNull(name.getTraitOrNull<SerdeTraits.XmlNameTrait>(SerdeTraits.XmlNameTrait.ID))
    }

    @Test
    fun testMemberCarriesOnlyItsOwnTraits() {
        // the target carries a trait; the member declares its own — a member reports ONLY its own
        val target = SimpleSchema(ShapeId("com.example#Named"), ShapeType.STRING, SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601))
        val schema = StructureSchema(ShapeId("com.example#Holder")) {
            member("field", target, SerdeTraits.JsonNameTrait("f"))
        }
        val field = assertNotNull(schema.member("field"))
        assertTrue(field.hasTrait(SerdeTraits.JsonNameTrait.ID))
        assertFalse(field.hasTrait(SerdeTraits.TimestampFormatTrait.ID)) // target traits are NOT merged in
        assertEquals(1, field.traits.size)
        // the target still carries its own trait
        assertTrue(field.target.hasTrait(SerdeTraits.TimestampFormatTrait.ID))
    }

    @Test
    fun testEffectiveTraitFallsBackToMemberTarget() {
        // @timestampFormat("date-time") timestamp Instant + structure Holder { at: Instant }
        val target = SimpleSchema(ShapeId("com.example#Instant"), ShapeType.TIMESTAMP, SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601))
        val schema = StructureSchema(ShapeId("com.example#Holder")) {
            member("at", target)
        }
        val at = assertNotNull(schema.member("at"))
        assertNull(at.getTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID))
        assertEquals(TimestampFormat.ISO_8601, at.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID)?.format)
    }

    @Test
    fun testEffectiveTraitPrefersTheMemberOverItsTarget() {
        val target = SimpleSchema(ShapeId("com.example#Instant"), ShapeType.TIMESTAMP, SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601))
        val schema = StructureSchema(ShapeId("com.example#Holder")) {
            member("at", target, SerdeTraits.TimestampFormatTrait(TimestampFormat.RFC_5322))
        }
        val at = assertNotNull(schema.member("at"))
        assertEquals(TimestampFormat.RFC_5322, at.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID)?.format)
    }

    @Test
    fun testEffectiveTraitAbsentFromMemberAndTarget() {
        val schema = StructureSchema(ShapeId("com.example#Holder")) {
            member("at", PreludeSchemas.Timestamp)
        }
        val at = assertNotNull(schema.member("at"))
        assertNull(at.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID))
    }

    @Test
    fun testEffectiveTraitOnNonMemberSchemaConsultsNothingElse() {
        val instant = SimpleSchema(ShapeId("com.example#Instant"), ShapeType.TIMESTAMP, SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601))
        val listId = ShapeId("com.example#InstantList")
        val list = ListSchema(listId, MemberSchema(listId.withMember("member"), instant))
        // an aggregate resolves only its own traits — it never reaches into the shapes it contains
        assertNull(list.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID))
        // while the element member, being a member, does reach its target
        assertEquals(
            TimestampFormat.ISO_8601,
            list.element.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID)?.format,
        )

        // the same holds for the target itself, which is not a member either
        assertNull(instant.getEffectiveTraitOrNull<SerdeTraits.JsonNameTrait>(SerdeTraits.JsonNameTrait.ID))
        assertEquals(TimestampFormat.ISO_8601, instant.getEffectiveTraitOrNull<SerdeTraits.TimestampFormatTrait>(SerdeTraits.TimestampFormatTrait.ID)?.format)
    }

    @Test
    fun testMapSchemaNavigation() {
        val mapId = ShapeId("com.example#StringMap")
        val schema = MapSchema(
            mapId,
            MemberSchema(mapId.withMember("key"), PreludeSchemas.String),
            MemberSchema(mapId.withMember("value"), PreludeSchemas.Integer),
        )
        assertEquals(ShapeType.MAP, schema.type)
        assertEquals(ShapeType.STRING, schema.key.target.type)
        assertEquals(ShapeType.INTEGER, schema.value.target.type)
    }

    @Test
    fun testUnionSchema() {
        val schema = UnionSchema(ShapeId("com.example#Shape")) {
            member("circle", PreludeSchemas.Double)
            member("square", PreludeSchemas.Double)
        }
        assertEquals(ShapeType.UNION, schema.type)
        assertEquals(2, schema.members.size)
        assertNotNull(schema.member("circle"))
    }

    @Test
    fun testRecursiveSchemaViaLazyMember() {
        // com.example#RecursiveValue { m: Map<String, RecursiveValue> }
        val mapId = ShapeId("com.example#RecursiveValueMap")
        lateinit var schema: StructureSchema
        schema = StructureSchema(ShapeId("com.example#RecursiveValue")) {
            member(
                "m",
                MapSchema(
                    mapId,
                    MemberSchema(mapId.withMember("key"), PreludeSchemas.String),
                    MemberSchema(mapId.withMember("value"), lazy { schema }),
                ),
            )
        }

        val m = assertNotNull(schema.member("m"))
        val map = m.target as MapSchema
        // resolving the self-referential value target does not blow up and points back at the structure
        assertSame(schema, map.value.target)
    }

    @Test
    fun testPreludeSchemasAreSimple() {
        assertTrue(PreludeSchemas.String.type.isSimple)
        assertEquals(ShapeType.BLOB, PreludeSchemas.Blob.type)
        assertTrue(PreludeSchemas.Blob.traits.isEmpty())
    }

    @Test
    fun testSimpleSchemaRejectsNonSimpleType() {
        assertFailsWith<IllegalArgumentException> {
            SimpleSchema(ShapeId("com.example#Nope"), ShapeType.STRUCTURE)
        }
    }

    @Test
    fun testAllPreludeSchemas() {
        val expected = mapOf(
            PreludeSchemas.Blob to ("smithy.api#Blob" to ShapeType.BLOB),
            PreludeSchemas.Boolean to ("smithy.api#Boolean" to ShapeType.BOOLEAN),
            PreludeSchemas.String to ("smithy.api#String" to ShapeType.STRING),
            PreludeSchemas.Timestamp to ("smithy.api#Timestamp" to ShapeType.TIMESTAMP),
            PreludeSchemas.Byte to ("smithy.api#Byte" to ShapeType.BYTE),
            PreludeSchemas.Short to ("smithy.api#Short" to ShapeType.SHORT),
            PreludeSchemas.Integer to ("smithy.api#Integer" to ShapeType.INTEGER),
            PreludeSchemas.Long to ("smithy.api#Long" to ShapeType.LONG),
            PreludeSchemas.Float to ("smithy.api#Float" to ShapeType.FLOAT),
            PreludeSchemas.Double to ("smithy.api#Double" to ShapeType.DOUBLE),
            PreludeSchemas.BigInteger to ("smithy.api#BigInteger" to ShapeType.BIG_INTEGER),
            PreludeSchemas.BigDecimal to ("smithy.api#BigDecimal" to ShapeType.BIG_DECIMAL),
            PreludeSchemas.Document to ("smithy.api#Document" to ShapeType.DOCUMENT),
        )
        for ((schema, idAndType) in expected) {
            val (id, type) = idAndType
            assertEquals(id, schema.shapeId.absoluteId)
            assertEquals(type, schema.type)
            assertTrue(schema.traits.isEmpty())
        }
    }

    @Test
    fun testContainerLevelTraits() {
        val schema = StructureSchema(ShapeId("com.example#S")) {
            trait(SerdeTraits.JsonNameTrait("s"))
            member("x", PreludeSchemas.String)
        }
        assertTrue(schema.hasTrait(SerdeTraits.JsonNameTrait.ID))
        assertEquals("s", schema.getTraitOrNull<SerdeTraits.JsonNameTrait>(SerdeTraits.JsonNameTrait.ID)?.value)
        // the member did not inherit the container's trait
        assertFalse(schema.member("x")!!.hasTrait(SerdeTraits.JsonNameTrait.ID))
    }

    @Test
    fun testMapKeyAndValueMemberNames() {
        val mapId = ShapeId("com.example#M")
        val schema = MapSchema(
            mapId,
            MemberSchema(mapId.withMember("key"), PreludeSchemas.String),
            MemberSchema(mapId.withMember("value"), PreludeSchemas.Integer),
        )
        assertEquals("key", schema.key.memberName)
        assertEquals("value", schema.value.memberName)
    }

    @Test
    fun testListElementMemberName() {
        val listId = ShapeId("com.example#L")
        val schema = ListSchema(listId, MemberSchema(listId.withMember("member"), PreludeSchemas.String))
        assertEquals("member", schema.element.memberName)
    }

    @Test
    fun testUnionMemberNotFound() {
        val schema = UnionSchema(ShapeId("com.example#U")) {
            member("a", PreludeSchemas.String)
        }
        assertNull(schema.member("missing"))
    }

    @Test
    fun testMemberShapeIdIsScopedToContainer() {
        val name = assertNotNull(birdSchema.member("name"))
        assertEquals("com.example#Bird\$name", name.shapeId.absoluteId)
        assertEquals("name", name.shapeId.member)
    }
}
