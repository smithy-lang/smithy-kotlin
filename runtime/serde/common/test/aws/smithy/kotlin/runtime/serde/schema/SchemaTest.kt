/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

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
    private val birdSchema: StructureSchema = StructureSchema(ShapeId.from("com.example#Bird")) {
        member("name", PreludeSchemas.String, JsonNameTrait("bird_name"))
        member(
            "colors",
            ListSchema(ShapeId.from("com.example#ColorList")) {
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
    fun testMemberLookupByIndex() {
        assertEquals("name", birdSchema.member(0)?.memberName)
        assertEquals("colors", birdSchema.member(1)?.memberName)
        assertNull(birdSchema.member(2))
        assertEquals(0, birdSchema.member("name")?.memberIndex)
        assertEquals(1, birdSchema.member("colors")?.memberIndex)
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
    fun testMemberTraitsMergeOverTarget() {
        // target string schema carries @sparse (contrived), member overrides jsonName
        val target = SimpleSchema(ShapeId.from("com.example#Named"), ShapeType.STRING, TimestampFormatTrait(TimestampFormat.DATE_TIME))
        val schema = StructureSchema(ShapeId.from("com.example#Holder")) {
            member("field", target, JsonNameTrait("f"))
        }
        val field = assertNotNull(schema.member("field"))
        // effective traits include both the member's own and the target's
        assertTrue(field.hasTrait(JsonNameTrait.ID))
        assertTrue(field.hasTrait(TimestampFormatTrait.ID))
        assertEquals(2, field.traits.size)
    }

    @Test
    fun testMapSchemaNavigation() {
        val schema = MapSchema(ShapeId.from("com.example#StringMap")) {
            key(PreludeSchemas.String)
            value(PreludeSchemas.Integer)
        }
        assertEquals(ShapeType.MAP, schema.type)
        assertEquals(ShapeType.STRING, schema.key.target.type)
        assertEquals(ShapeType.INTEGER, schema.value.target.type)
    }

    @Test
    fun testUnionSchema() {
        val schema = UnionSchema(ShapeId.from("com.example#Shape")) {
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
        schema = StructureSchema(ShapeId.from("com.example#RecursiveValue")) {
            member(
                "m",
                MapSchema(ShapeId.from("com.example#RecursiveValueMap")) {
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
            SimpleSchema(ShapeId.from("com.example#Nope"), ShapeType.STRUCTURE)
        }
    }

    @Test
    fun testDocumentTraitEnumerable() {
        val custom = ShapeId.from("com.example#customTrait")
        val schema = StructureSchema(ShapeId.from("com.example#Tagged")) {
            member("x", PreludeSchemas.String, DocumentTrait(custom, null))
        }
        val x = assertNotNull(schema.member("x"))
        assertTrue(x.hasTrait(custom))
        assertNotNull(x.getTrait<DocumentTrait>(custom))
    }
}
