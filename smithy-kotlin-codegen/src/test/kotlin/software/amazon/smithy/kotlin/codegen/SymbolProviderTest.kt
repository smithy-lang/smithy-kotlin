/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.StreamingTrait

class SymbolProviderTest {
    @Test
    fun `escapes reserved member names`() {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$class").target("smithy.api#String").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val actual = provider.toMemberName(member)
        assertEquals("`class`", actual)
    }

    @Test
    fun `creates symbols in correct namespace`() {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#String").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val structSymbol = provider.toSymbol(struct)
        val memberSymbol = provider.toSymbol(member)
        assertEquals("test.model", structSymbol.namespace)
        assertEquals(".", structSymbol.namespaceDelimiter)

        // builtins should not have a namespace set
        assertEquals("", memberSymbol.namespace)
    }

    @DisplayName("Creates primitives")
    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @CsvSource(
        "String, null, true",
        "Integer, null, true",
        "PrimitiveInteger, 0, false",
        "Short, null, true",
        "PrimitiveShort, 0, false",
        "Long, null, true",
        "PrimitiveLong, 0, false",
        "Byte, null, true",
        "PrimitiveByte, 0, false",
        "Float, null, true",
        "PrimitiveFloat, 0.0f, false",
        "Double, null, true",
        "PrimitiveDouble, 0.0, false",
        "Boolean, null, true",
        "PrimitiveBoolean, false, false"
    )
    fun `creates primitives`(primitiveType: String, expectedDefault: String, boxed: Boolean) {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#$primitiveType").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val memberSymbol = provider.toSymbol(member)
        // builtins should not have a namespace set
        assertEquals("", memberSymbol.namespace)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
        assertEquals(boxed, memberSymbol.isBoxed())

        val expectedName = translateTypeName(primitiveType.removePrefix("Primitive"))
        assertEquals(expectedName, memberSymbol.name)
    }

    // the Kotlin type names pretty much match 1-1 with the smithy types for numerics (except Int), string, and boolean
    private fun translateTypeName(typeName: String): String = when (typeName) {
            "Integer" -> "Int"
            else -> typeName
        }

    @Test
    fun `creates blobs`() {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#Blob").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val memberSymbol = provider.toSymbol(member)
        // builtins should not have a namespace set
        assertEquals("", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertEquals(true, memberSymbol.isBoxed())

        assertEquals("ByteArray", memberSymbol.name)
    }

    @Test
    fun `creates streaming blobs`() {

        val blobStream = BlobShape.builder().id("foo.bar#BodyStream").addTrait(StreamingTrait()).build()

        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target(blobStream).build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member, blobStream)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val memberSymbol = provider.toSymbol(member)

        assertEquals("software.aws.clientrt.content", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertEquals(true, memberSymbol.isBoxed())
        assertEquals("ByteStream", memberSymbol.name)
        val dependency = memberSymbol.dependencies[0].expectProperty("dependency") as KotlinDependency
        assertEquals("client-rt-core", dependency.artifact)
    }

    @Test
    fun `creates lists`() {
        val struct = StructureShape.builder().id("foo.bar#Record").build()
        val listMember = MemberShape.builder().id("foo.bar#Records\$member").target(struct).build()
        val list = ListShape.builder()
            .id("foo.bar#Records")
            .member(listMember)
            .build()
        val model = Model.assembler()
            .addShapes(list, listMember, struct)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val listSymbol = provider.toSymbol(list)

        assertEquals("List<Record>", listSymbol.name)
        assertEquals(true, listSymbol.isBoxed())
        assertEquals("null", listSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", listSymbol.references[0].symbol.name)
    }

    @Test
    fun `creates sets`() {
        val struct = StructureShape.builder().id("foo.bar#Record").build()
        val setMember = MemberShape.builder().id("foo.bar#Records\$member").target(struct).build()
        val set = SetShape.builder()
            .id("foo.bar#Records")
            .member(setMember)
            .build()
        val model = Model.assembler()
            .addShapes(set, setMember, struct)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val setSymbol = provider.toSymbol(set)

        assertEquals("Set<Record>", setSymbol.name)
        assertEquals(true, setSymbol.isBoxed())
        assertEquals("null", setSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", setSymbol.references[0].symbol.name)
    }

    @Test
    fun `creates maps`() {
        val struct = StructureShape.builder().id("foo.bar#Record").build()
        val keyMember = MemberShape.builder().id("foo.bar#MyMap\$key").target("smithy.api#String").build()
        val valueMember = MemberShape.builder().id("foo.bar#MyMap\$value").target(struct).build()
        val map = MapShape.builder()
            .id("foo.bar#MyMap")
            .key(keyMember)
            .value(valueMember)
            .build()
        val model = Model.assembler()
            .addShapes(map, keyMember, valueMember, struct)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val mapSymbol = provider.toSymbol(map)

        assertEquals("Map<String, Record>", mapSymbol.name)
        assertEquals(true, mapSymbol.isBoxed())
        assertEquals("null", mapSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", mapSymbol.references[0].symbol.name)
    }

    @DisplayName("creates bigNumbers")
    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @ValueSource(strings = ["BigInteger", "BigDecimal"])
    fun `creates bigNumbers`(type: String) {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#$type").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val bigSymbol = provider.toSymbol(member)
        assertEquals("java.math", bigSymbol.namespace)
        assertEquals("null", bigSymbol.defaultValue())
        assertEquals(true, bigSymbol.isBoxed())
        assertEquals("$type", bigSymbol.name)
    }

    @Test
    fun `creates enums`() {
        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("FOO").build())
            .addEnum(EnumDefinition.builder().value("BAR").build())
            .build()

        val shape = StringShape.builder()
            .id("com.test#Baz")
            .addTrait(trait)
            .build()

        val model = Model.assembler()
            .addShapes(shape)
            .assemble()
            .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(shape)

        assertEquals("test.model", symbol.namespace)
        assertEquals("null", symbol.defaultValue())
        assertEquals(true, symbol.isBoxed())
        assertEquals("Baz", symbol.name)
        assertEquals("Baz.kt", symbol.definitionFile)
    }

    @Test
    fun `creates unions`() {
        val member1 = MemberShape.builder().id("com.test#MyUnion\$foo").target("smithy.api#String").build()
        val member2 = MemberShape.builder().id("com.test#MyUnion\$bar").target("smithy.api#PrimitiveInteger").build()
        val member3 = MemberShape.builder().id("com.test#MyUnion\$baz").target("smithy.api#Integer").build()

        val union = UnionShape.builder()
                .id("com.test#MyUnion")
                .addMember(member1)
                .addMember(member2)
                .addMember(member3)
                .build()
        val model = Model.assembler()
                .addShapes(union, member1, member2, member3)
                .assemble()
                .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(union)

        assertEquals("test.model", symbol.namespace)
        assertEquals("null", symbol.defaultValue())
        assertEquals(true, symbol.isBoxed())
        assertEquals("MyUnion", symbol.name)
        assertEquals("MyUnion.kt", symbol.definitionFile)
    }

    @Test
    fun `creates structures`() {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#String").build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val structSymbol = provider.toSymbol(struct)
        assertEquals("test.model", structSymbol.namespace)
        assertEquals("MyStruct", structSymbol.name)
        assertEquals("null", structSymbol.defaultValue())
        assertEquals(true, structSymbol.isBoxed())
        assertEquals("MyStruct.kt", structSymbol.definitionFile)
        assertEquals(1, structSymbol.references.size)
    }

    @Test
    fun `creates documents`() {
        val document = DocumentShape.builder().id("foo.bar#MyDocument").build()
        val model = Model.assembler()
            .addShapes(document)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val documentSymbol = provider.toSymbol(document)
        assertEquals("Document", documentSymbol.name)
        assertEquals("null", documentSymbol.defaultValue())
        assertEquals(true, documentSymbol.isBoxed())
        assertEquals("${KotlinDependency.CLIENT_RT_CORE.namespace}.smithy", documentSymbol.namespace)
        assertEquals(1, documentSymbol.dependencies.size)
    }

    @Test
    fun `structures references inner collection members`() {
        // lists/sets reference the inner member, ensure that struct members
        // also contain a reference to the collection member in addition to the
        // collection itself
        /*
            timestamp MyTimestamp

            list Records {
                member: MyTimestamp
            }

            structure MyStruct {
                quux: Records
            }


            // e.g.
            import clientrt.Instant    <-- ensure we get this reference
            class MyStruct {
                quux: List<Instant>? = ...
            }

         */

        val ts = TimestampShape.builder().id("foo.bar#MyTimestamp").build()
        val listMember = MemberShape.builder().id("foo.bar#Records\$member").target(ts).build()
        val list = ListShape.builder()
            .id("foo.bar#Records")
            .member(listMember)
            .build()

        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target(list).build()
        val struct = StructureShape.builder()
            .id("foo.bar#MyStruct")
            .addMember(member)
            .build()
        val model = Model.assembler()
            .addShapes(struct, member, list, listMember, ts)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val structSymbol = provider.toSymbol(struct)
        // should get reference to List and Instant
        assertEquals(2, structSymbol.references.size)
    }

    @Test
    fun `creates timestamps`() {
        val tsShape = TimestampShape.builder().id("foo.bar#MyTimestamp").build()

        val model = Model.assembler()
            .addShapes(tsShape)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val timestampSymbol = provider.toSymbol(tsShape)
        assertEquals("software.aws.clientrt.time", timestampSymbol.namespace)
        assertEquals("Instant", timestampSymbol.name)
        assertEquals("null", timestampSymbol.defaultValue())
        assertEquals(true, timestampSymbol.isBoxed())
        assertEquals(1, timestampSymbol.dependencies.size)
    }

    @Test
    fun `it handles recursive structures`() {
        /*
            structure MyStruct1{
                quux: String,
                nested: MyStruct2
            }

            structure MyStruct2 {
                bar: String,
                recursiveMember: MyStruct1
            }
        */
        val memberQuux = MemberShape.builder().id("foo.bar#MyStruct1\$quux").target("smithy.api#String").build()
        val nestedMember = MemberShape.builder().id("foo.bar#MyStruct1\$nested").target("foo.bar#MyStruct2").build()
        val struct1 = StructureShape.builder()
            .id("foo.bar#MyStruct1")
            .addMember(memberQuux)
            .addMember(nestedMember)
            .build()

        val memberBar = MemberShape.builder().id("foo.bar#MyStruct2\$bar").target("smithy.api#String").build()
        val recursiveMember = MemberShape.builder().id("foo.bar#MyStruct2\$recursiveMember").target("foo.bar#MyStruct1").build()
        val struct2 = StructureShape.builder()
            .id("foo.bar#MyStruct2")
            .addMember(memberBar)
            .addMember(recursiveMember)
            .build()
        val model = Model.assembler()
            .addShapes(struct1, memberQuux, nestedMember, struct2, memberBar, recursiveMember)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")

        val structSymbol = provider.toSymbol(struct1)
        assertEquals("test.model", structSymbol.namespace)
        assertEquals("MyStruct1", structSymbol.name)
        assertEquals("null", structSymbol.defaultValue())
        assertEquals(true, structSymbol.isBoxed())
        assertEquals("MyStruct1.kt", structSymbol.definitionFile)
        assertEquals(2, structSymbol.references.size)
    }
}
