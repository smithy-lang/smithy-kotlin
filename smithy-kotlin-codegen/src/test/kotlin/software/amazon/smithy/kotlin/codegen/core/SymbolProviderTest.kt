/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.model.ext.defaultValue
import software.amazon.smithy.kotlin.codegen.model.ext.expectShape
import software.amazon.smithy.kotlin.codegen.model.ext.isBoxed
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.StreamingTrait

class SymbolProviderTest {
    @Test
    fun `escapes reserved member names`() {
        val model = """
        structure MyStruct {
            class: String
        }
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$class")
        val actual = provider.toMemberName(member)
        assertEquals("`class`", actual)
    }

    @Test
    fun `creates symbols in correct namespace`() {
        val model = """
        structure MyStruct {
            quux: String
        }
            
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val struct = model.expectShape<StructureShape>("com.test#MyStruct")
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$quux")
        val structSymbol = provider.toSymbol(struct)
        val memberSymbol = provider.toSymbol(member)
        assertEquals("com.test.model", structSymbol.namespace)
        assertEquals(".", structSymbol.namespaceDelimiter)
        assertEquals("kotlin", memberSymbol.namespace)
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
        "PrimitiveLong, 0L, false",
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
        val model = """
            structure MyStruct {
                quux: $primitiveType,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val member = model.expectShape<MemberShape>("foo.bar#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
        assertEquals(boxed, memberSymbol.isBoxed)

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
        val model = """
        structure MyStruct {
            quux: Blob
        }
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertEquals(true, memberSymbol.isBoxed)
        assertEquals("ByteArray", memberSymbol.name)
    }

    @Test
    fun `creates streaming blobs`() {
        val blobStream = BlobShape.builder().id("foo.bar#BodyStream").addTrait(StreamingTrait()).build()
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target(blobStream).build()

        val model = """
            structure MyStruct {
                quux: BodyStream,
            }

            @streaming
            blob BodyStream
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val memberSymbol = provider.toSymbol(member)

        assertEquals("software.aws.clientrt.content", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertEquals(true, memberSymbol.isBoxed)
        assertEquals("ByteStream", memberSymbol.name)
        val dependency = memberSymbol.dependencies[0].expectProperty("dependency") as KotlinDependency
        assertEquals("client-rt-core", dependency.artifact)
    }

    @Test
    fun `creates lists`() {
        val model = """
            structure Record {}

            list Records {
                member: Record,
            }
            
            @sparse
            list SparseRecords {
                member: Record,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val listSymbol = provider.toSymbol(model.expectShape<ListShape>("foo.bar#Records"))

        assertEquals("List<Record>", listSymbol.name)
        assertEquals(true, listSymbol.isBoxed)
        assertEquals("null", listSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", listSymbol.references[0].symbol.name)

        val sparseListSymbol = provider.toSymbol(model.expectShape<ListShape>("foo.bar#SparseRecords"))

        assertEquals("List<Record?>", sparseListSymbol.name)
        assertEquals(true, sparseListSymbol.isBoxed)
        assertEquals("null", sparseListSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", sparseListSymbol.references[0].symbol.name)
    }

    @Test
    fun `creates sets`() {
        val struct = StructureShape.builder().id("foo.bar#Record").build()
        val setMember = MemberShape.builder().id("foo.bar#Records\$member").target(struct).build()
        val set = SetShape.builder()
            .id("foo.bar#Records")
            .member(setMember)
            .build()
        val model = """
            structure Record { }

            set Records {
                member: Record,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val setSymbol = provider.toSymbol(set)

        assertEquals("Set<Record>", setSymbol.name)
        assertEquals(true, setSymbol.isBoxed)
        assertEquals("null", setSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", setSymbol.references[0].symbol.name)
    }

    @Test
    fun `creates maps`() {
        val model = """
            structure Record {}

            map MyMap {
                key: String,
                value: Record,
            }
            
            @sparse
            map MySparseMap {
                key: String,
                value: Record,
            }
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)

        val mapSymbol = provider.toSymbol(model.expectShape<MapShape>("${TestModelDefault.NAMESPACE}#MyMap"))

        assertEquals("Map<String, Record>", mapSymbol.name)
        assertEquals(true, mapSymbol.isBoxed)
        assertEquals("null", mapSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", mapSymbol.references[0].symbol.name)

        val sparseMapSymbol = provider.toSymbol(model.expectShape<MapShape>("${TestModelDefault.NAMESPACE}#MySparseMap"))

        assertEquals("Map<String, Record?>", sparseMapSymbol.name)
        assertEquals(true, sparseMapSymbol.isBoxed)
        assertEquals("null", sparseMapSymbol.defaultValue())

        // collections should contain a reference to the member type
        assertEquals("Record", sparseMapSymbol.references[0].symbol.name)
    }

    @DisplayName("creates bigNumbers")
    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @ValueSource(strings = ["BigInteger", "BigDecimal"])
    fun `creates bigNumbers`(type: String) {
        val member = MemberShape.builder().id("foo.bar#MyStruct\$quux").target("smithy.api#$type").build()
        val model = """
            structure MyStruct {
                quux: $type,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val bigSymbol = provider.toSymbol(member)
        assertEquals("java.math", bigSymbol.namespace)
        assertEquals("null", bigSymbol.defaultValue())
        assertEquals(true, bigSymbol.isBoxed)
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

        val model = """
            @enum([
                {
                    value: "FOO",
                },
                {
                    value: "BAR",
                },
            ])
            string Baz
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val symbol = provider.toSymbol(shape)

        assertEquals("foo.bar.model", symbol.namespace)
        assertEquals("null", symbol.defaultValue())
        assertEquals(true, symbol.isBoxed)
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
        val model = """
            union MyUnion {
                foo: String,
                bar: PrimitiveInteger,
                baz: Integer,
            }
        """.prependNamespaceAndService().toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model)
        val symbol = provider.toSymbol(union)

        assertEquals("com.test.model", symbol.namespace)
        assertEquals("null", symbol.defaultValue())
        assertEquals(true, symbol.isBoxed)
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
        val model = """
            structure MyStruct {
                quux: String,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val structSymbol = provider.toSymbol(struct)
        assertEquals("foo.bar.model", structSymbol.namespace)
        assertEquals("MyStruct", structSymbol.name)
        assertEquals("null", structSymbol.defaultValue())
        assertEquals(true, structSymbol.isBoxed)
        assertEquals("MyStruct.kt", structSymbol.definitionFile)
        assertEquals(1, structSymbol.references.size)
    }

    @Test
    fun `creates documents`() {
        val document = DocumentShape.builder().id("foo.bar#MyDocument").build()
        val model = """
            document MyDocument
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val documentSymbol = provider.toSymbol(document)
        assertEquals("Document", documentSymbol.name)
        assertEquals("null", documentSymbol.defaultValue())
        assertEquals(true, documentSymbol.isBoxed)
        assertEquals("${KotlinDependency.CLIENT_RT_CORE.namespace}.smithy", documentSymbol.namespace)
        assertEquals(1, documentSymbol.dependencies.size)
    }

    @Test
    fun `structures references inner collection members`() {
        // lists/sets reference the inner member, ensure that struct members
        // also contain a reference to the collection member in addition to the
        // collection itself
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

        val model = """
            structure MyStruct {
                quux: Records,
            }

            list Records {
                member: MyTimestamp,
            }

            timestamp MyTimestamp
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val structSymbol = provider.toSymbol(struct)
        // should get reference to List and Instant
        assertEquals(2, structSymbol.references.size)
    }

    @Test
    fun `creates timestamps`() {
        val tsShape = TimestampShape.builder().id("foo.bar#MyTimestamp").build()

        val model = "timestamp MyTimestamp".prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val timestampSymbol = provider.toSymbol(tsShape)
        assertEquals("software.aws.clientrt.time", timestampSymbol.namespace)
        assertEquals("Instant", timestampSymbol.name)
        assertEquals("null", timestampSymbol.defaultValue())
        assertEquals(true, timestampSymbol.isBoxed)
        assertEquals(1, timestampSymbol.dependencies.size)
    }

    @Test
    fun `it handles recursive structures`() {
        val memberQuux = MemberShape.builder().id("foo.bar#MyStruct1\$quux").target("smithy.api#String").build()
        val nestedMember = MemberShape.builder().id("foo.bar#MyStruct1\$nested").target("foo.bar#MyStruct2").build()
        val struct1 = StructureShape.builder()
            .id("foo.bar#MyStruct1")
            .addMember(memberQuux)
            .addMember(nestedMember)
            .build()

        val model = """
            structure MyStruct1 {
                quux: String,
                nested: MyStruct2,
            }

            structure MyStruct2 {
                bar: String,
                recursiveMember: MyStruct1,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")

        val structSymbol = provider.toSymbol(struct1)
        assertEquals("foo.bar.model", structSymbol.namespace)
        assertEquals("MyStruct1", structSymbol.name)
        assertEquals("null", structSymbol.defaultValue())
        assertEquals(true, structSymbol.isBoxed)
        assertEquals("MyStruct1.kt", structSymbol.definitionFile)
        assertEquals(2, structSymbol.references.size)
    }
}
