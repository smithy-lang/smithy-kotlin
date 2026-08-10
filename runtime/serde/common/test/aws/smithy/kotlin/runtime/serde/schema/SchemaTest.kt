/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.JsonNameTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormat
import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormatTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.XmlNameTrait
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
    private val birdSchema: StructureSchema = StructureSchema(shapeId("com.example#Bird")) {
        member("name", PreludeSchemas.String, JsonNameTrait("bird_name"))
        member(
            "colors",
            ListSchema(shapeId("com.example#ColorList")) {
                element(PreludeSchemas.String)
            },
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
        assertTrue(name.hasTrait(JsonNameTrait.ID))
        val jsonName = assertNotNull(name.getTrait<JsonNameTrait>(JsonNameTrait.ID))
        assertEquals("bird_name", jsonName.value)

        assertFalse(name.hasTrait(XmlNameTrait.ID))
        assertNull(name.getTrait<XmlNameTrait>(XmlNameTrait.ID))
    }

    @Test
    fun testMemberCarriesOnlyItsOwnTraits() {
        // the target carries a trait; the member declares its own — a member reports ONLY its own
        val target = SimpleSchema(shapeId("com.example#Named"), ShapeType.STRING, TimestampFormatTrait(TimestampFormat.DATE_TIME))
        val schema = StructureSchema(shapeId("com.example#Holder")) {
            member("field", target, JsonNameTrait("f"))
        }
        val field = assertNotNull(schema.member("field"))
        assertTrue(field.hasTrait(JsonNameTrait.ID))
        assertFalse(field.hasTrait(TimestampFormatTrait.ID)) // target traits are NOT merged in
        assertEquals(1, field.traits.size)
        // the target still carries its own trait
        assertTrue(field.target.hasTrait(TimestampFormatTrait.ID))
    }

    @Test
    fun testMapSchemaNavigation() {
        val schema = MapSchema(shapeId("com.example#StringMap")) {
            key(PreludeSchemas.String)
            value(PreludeSchemas.Integer)
        }
        assertEquals(ShapeType.MAP, schema.type)
        assertEquals(ShapeType.STRING, schema.key.target.type)
        assertEquals(ShapeType.INTEGER, schema.value.target.type)
    }

    @Test
    fun testUnionSchema() {
        val schema = UnionSchema(shapeId("com.example#Shape")) {
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
        lateinit var schema: StructureSchema
        schema = StructureSchema(shapeId("com.example#RecursiveValue")) {
            member(
                "m",
                MapSchema(shapeId("com.example#RecursiveValueMap")) {
                    key(PreludeSchemas.String)
                    value(lazy { schema })
                },
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
            SimpleSchema(shapeId("com.example#Nope"), ShapeType.STRUCTURE)
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
        val schema = StructureSchema(shapeId("com.example#S")) {
            trait(JsonNameTrait("s"))
            member("x", PreludeSchemas.String)
        }
        assertTrue(schema.hasTrait(JsonNameTrait.ID))
        assertEquals("s", schema.getTrait<JsonNameTrait>(JsonNameTrait.ID)?.value)
        // the member did not inherit the container's trait
        assertFalse(schema.member("x")!!.hasTrait(JsonNameTrait.ID))
    }

    @Test
    fun testMapKeyAndValueMemberNames() {
        val schema = MapSchema(shapeId("com.example#M")) {
            key(PreludeSchemas.String)
            value(PreludeSchemas.Integer)
        }
        assertEquals("key", schema.key.memberName)
        assertEquals("value", schema.value.memberName)
    }

    @Test
    fun testListElementMemberName() {
        val schema = ListSchema(shapeId("com.example#L")) {
            element(PreludeSchemas.String)
        }
        assertEquals("member", schema.element.memberName)
    }

    @Test
    fun testUnionMemberNotFound() {
        val schema = UnionSchema(shapeId("com.example#U")) {
            member("a", PreludeSchemas.String)
        }
        assertNull(schema.member("missing"))
    }

    @Test
    fun testListMissingElementFails() {
        assertFailsWith<IllegalArgumentException> {
            ListSchema(shapeId("com.example#L")) { /* no element */ }
        }
    }

    @Test
    fun testMapMissingKeyOrValueFails() {
        assertFailsWith<IllegalArgumentException> {
            MapSchema(shapeId("com.example#M")) { value(PreludeSchemas.String) } // no key
        }
        assertFailsWith<IllegalArgumentException> {
            MapSchema(shapeId("com.example#M")) { key(PreludeSchemas.String) } // no value
        }
    }

    @Test
    fun testMemberShapeIdIsScopedToContainer() {
        val name = assertNotNull(birdSchema.member("name"))
        assertEquals("com.example#Bird\$name", name.shapeId.absoluteId)
        assertEquals("name", name.shapeId.member)
    }
}
