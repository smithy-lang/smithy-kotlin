/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.core

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.ApiSettings
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency.Companion.CORE
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.model.traits.SYNTHETIC_NAMESPACE
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.knowledge.NullableIndex.CheckMode
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.ClientOptionalTrait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        "PrimitiveShort, 0.toShort(), false",
        "Long, null, true",
        "PrimitiveLong, 0L, false",
        "Byte, null, true",
        "PrimitiveByte, 0.toByte(), false",
        "Float, null, true",
        "PrimitiveFloat, 0f, false",
        "Double, null, true",
        "PrimitiveDouble, 0.0, false",
        "Boolean, null, true",
        "PrimitiveBoolean, false, false",
    )
    fun `creates primitives`(primitiveType: String, expectedDefault: String, nullable: Boolean) {
        // IDLv2.0 requires modeling a default value on primitives
        val defaultTrait = when {
            primitiveType == "PrimitiveBoolean" -> "@default(false)"
            primitiveType.startsWith("Primitive") -> "@default(0)"
            else -> ""
        }

        val model = """
            structure MyStruct {
                $defaultTrait
                quux: $primitiveType,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val member = model.expectShape<MemberShape>("foo.bar#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
        assertEquals(nullable, memberSymbol.isNullable)

        val expectedName = translateTypeName(primitiveType.removePrefix("Primitive"))
        assertEquals(expectedName, memberSymbol.name)
    }

    // the Kotlin type names pretty much match 1-1 with the smithy types for numerics (except Int), string, and boolean
    private fun translateTypeName(typeName: String): String = when (typeName) {
        "Integer" -> "Int"
        else -> typeName
    }

    @Test
    fun `can read default trait from member`() {
        val modeledDefault = "5"
        val expectedDefault = "5L"

        val model = """
        structure MyStruct {
           @default($modeledDefault)
           foo: MyFoo
        }
        
        long MyFoo
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
        assertFalse(memberSymbol.isNullable)
    }

    @Test
    fun `can read default trait from target`() {
        val modeledDefault = "2500"
        val expectedDefault = "2500L"

        val model = """
        structure MyStruct {
           @default($modeledDefault)
           foo: MyFoo
        }
        
        @default($modeledDefault)
        long MyFoo
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
    }

    @Test
    fun `can override default trait from root-level shape`() {
        val modeledDefault = "2500"

        val model = """
        structure MyStruct {
           @default(null)
           foo: RootLevelShape
        }
        
        @default($modeledDefault)
        long RootLevelShape
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertTrue(memberSymbol.isNullable)
    }

    @ParameterizedTest(name = "{index} ==> ''can default simple {0} type''")
    @CsvSource(
        "long,100,100L",
        "integer,5,5",
        "short,32767,32767.toShort()",
        "float,3.14159,3.14159f",
        "double,2.71828,2.71828",
        "byte,10,10.toByte()",
        "string,\"hello\",\"hello\"",
        "blob,\"abcdefg\",\"abcdefg\"",
        "boolean,true,true",
        "bigInteger,5,5",
        "bigDecimal,9.0123456789,9.0123456789",
        "timestamp,1684869901,1684869901",
    )
    fun `can default simple types`(typeName: String, modeledDefault: String, expectedDefault: String) {
        val model = """
        structure MyStruct {
           @default($modeledDefault)
           foo: Shape
        }
        
        @default($modeledDefault)
        $typeName Shape
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
    }

    @Test
    fun `can default empty string`() {
        val model = """
        structure MyStruct {
           @default("")
           foo: myString
        }
        string myString
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("\"\"", memberSymbol.defaultValue())
    }

    @Test
    fun `can default enum type`() {
        val model = """
        structure MyStruct {
           @default("club")
           foo: Suit
        }

        enum Suit {
            @enumValue("diamond")
            DIAMOND
        
            @enumValue("club")
            CLUB
        
            @enumValue("heart")
            HEART
        
            @enumValue("spade")
            SPADE
        }        
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("""com.test.model.Suit.fromValue("club")""", memberSymbol.defaultValue())
    }

    @Test
    fun `can default int enum type`() {
        val model = """
        structure MyStruct {
           @default(2)
           foo: Season
        }

        intEnum Season {
            SPRING = 1
            SUMMER = 2
            FALL = 3
            WINTER = 4 
        }        
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("com.test.model.Season.fromValue(2)", memberSymbol.defaultValue())
    }

    @ParameterizedTest(name = "{index} ==> ''can default document with {0} type''")
    @CsvSource(
        "boolean,true,true",
        "boolean,false,false",
        "string,\"hello\",\"hello\"",
        "long,100,100",
        "integer,5,5",
        "short,32767,32767",
        "float,3.14159,3.14159",
        "double,2.71828,2.71828",
        "byte,10,10",
        "list,[],listOf()",
        "map,{},mapOf()",
    )
    @Suppress("UNUSED_PARAMETER") // using the first parameter in the test name, but compiler doesn't acknowledge that
    fun `can default document type`(typeName: String, modeledDefault: String, expectedDefault: String) {
        val model = """
        structure MyStruct {
           @default($modeledDefault)
           foo: MyDocument
        }

        document MyDocument
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals(expectedDefault, memberSymbol.defaultValue())
    }

    @Test
    fun `can default list type`() {
        val model = """
        structure MyStruct {
           @default([])
           foo: MyStringList
        }

        list MyStringList {
            member: MyString
        }

        string MyString
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("listOf()", memberSymbol.defaultValue())
    }

    @Test
    fun `can default map type`() {
        val model = """
        structure MyStruct {
           @default({})
           foo: MyStringToIntegerMap
        }

        map MyStringToIntegerMap {
            key: MyString
            value: MyInteger
        }

        string MyString
        integer MyInteger
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$foo")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("mapOf()", memberSymbol.defaultValue())
    }

    @Test
    fun `@clientOptional`() {
        val model = """
        structure MyStruct {
            @required
            @clientOptional
            quux: QuuxType
        }
        
        string QuuxType
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertTrue(memberSymbol.isNullable)
        assertEquals("null", memberSymbol.defaultValue())
    }

    @Test
    fun `@clientOptional overrides @default`() {
        val model = """
        structure MyStruct {
            @required
            @clientOptional
            @default("Foo")
            quux: QuuxType
        }

        string QuuxType
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertTrue(memberSymbol.isNullable)
        assertEquals("null", memberSymbol.defaultValue())
    }

    @Test
    fun `@input`() {
        val model = """
        @input
        structure MyStruct {
            @required
            @default(2)
            quux: QuuxType
        }
        
        long QuuxType
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val member = model.expectShape<MemberShape>("com.test#MyStruct\$quux")
        val memberSymbol = provider.toSymbol(member)
        assertEquals("kotlin", memberSymbol.namespace)
        assertTrue(memberSymbol.isNullable)
        assertEquals("null", memberSymbol.defaultValue())
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
        assertTrue(memberSymbol.isNullable)
        assertEquals("ByteArray", memberSymbol.name)
    }

    @Test
    fun `creates streaming blobs`() {
        val model = """
            structure MyStruct {
                @required
                quux: BodyStream,
            }

            @streaming
            blob BodyStream
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val member = model.expectShape<MemberShape>("foo.bar#MyStruct\$quux")
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val memberSymbol = provider.toSymbol(member)

        assertEquals("$RUNTIME_ROOT_NS.content", memberSymbol.namespace)
        assertEquals("null", memberSymbol.defaultValue())
        assertEquals(true, memberSymbol.isNullable)
        assertEquals("ByteStream", memberSymbol.name)
        val dependency = memberSymbol.dependencies[0].expectProperty("dependency") as KotlinDependency
        assertEquals(CORE.artifact, dependency.artifact)
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

        // collections should contain a reference to the member type
        assertEquals("Record", listSymbol.references[0].symbol.name)

        val sparseListSymbol = provider.toSymbol(model.expectShape<ListShape>("foo.bar#SparseRecords"))

        assertEquals("List<Record?>", sparseListSymbol.name)

        // collections should contain a reference to the member type
        assertEquals("Record", sparseListSymbol.references[0].symbol.name)

        // check the fully qualified name hint is set
        assertEquals("List<foo.bar.model.Record>", listSymbol.fullNameHint)
        assertEquals("List<foo.bar.model.Record?>", sparseListSymbol.fullNameHint)
    }

    @Test
    fun `creates lists even when @uniqueItems is present`() {
        val model = """
            structure Record { }

            @uniqueItems
            list Records {
                member: Record,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val listShape = model.expectShape<ListShape>("foo.bar#Records")
        val listSymbol = provider.toSymbol(listShape)

        assertEquals("List<Record>", listSymbol.name)

        // collections should contain a reference to the member type
        assertEquals("Record", listSymbol.references[0].symbol.name)
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

        // collections should contain a reference to the member type
        assertEquals("Record", mapSymbol.references[0].symbol.name)

        val sparseMapSymbol = provider.toSymbol(model.expectShape<MapShape>("${TestModelDefault.NAMESPACE}#MySparseMap"))

        assertEquals("Map<String, Record?>", sparseMapSymbol.name)

        // collections should contain a reference to the member type
        assertEquals("Record", sparseMapSymbol.references[0].symbol.name)

        // check the fully qualified name hint is set
        assertEquals("Map<kotlin.String, com.test.model.Record>", mapSymbol.fullNameHint)
        assertEquals("Map<kotlin.String, com.test.model.Record?>", sparseMapSymbol.fullNameHint)
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
        assertEquals("aws.smithy.kotlin.runtime.content", bigSymbol.namespace)
        assertEquals("null", bigSymbol.defaultValue())
        assertEquals(true, bigSymbol.isNullable)
        assertEquals(type, bigSymbol.name)
    }

    @Test
    fun `creates enums`() {
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
        val shape = model.expectShape<StringShape>("foo.bar#Baz")
        val symbol = provider.toSymbol(shape)

        assertEquals("foo.bar.model", symbol.namespace)
        assertEquals("Baz", symbol.name)
        assertEquals("Baz.kt", symbol.definitionFile)
    }

    @Test
    fun `creates int enums`() {
        val model = """
            intEnum Baz {
                FOO = 1
                BAR = 2
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val shape = model.expectShape<IntEnumShape>("foo.bar#Baz")
        val symbol = provider.toSymbol(shape)

        assertEquals("foo.bar.model", symbol.namespace)
        assertEquals("Baz", symbol.name)
        assertEquals("Baz.kt", symbol.definitionFile)
    }

    @Test
    fun `creates unions`() {
        val model = """
            union MyUnion {
                foo: String,
                bar: PrimitiveInteger,
                baz: Integer,
            }
        """.prependNamespaceAndService().toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model)
        val unionShape = model.expectShape<UnionShape>("com.test#MyUnion")
        val symbol = provider.toSymbol(unionShape)

        assertEquals("com.test.model", symbol.namespace)
        assertEquals("MyUnion", symbol.name)
        assertEquals("MyUnion.kt", symbol.definitionFile)
    }

    @Test
    fun `creates structures`() {
        val model = """
            structure MyStruct {
                quux: String,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val structShape = model.expectShape<StructureShape>("foo.bar#MyStruct")
        val structSymbol = provider.toSymbol(structShape)
        assertEquals("foo.bar.model", structSymbol.namespace)
        assertEquals("MyStruct", structSymbol.name)
        assertEquals("MyStruct.kt", structSymbol.definitionFile)
        assertEquals(1, structSymbol.references.size)
    }

    @Test
    fun `creates documents`() {
        val model = """
            document MyDocument
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val documentShape = model.expectShape<DocumentShape>("foo.bar#MyDocument")
        val documentSymbol = provider.toSymbol(documentShape)
        assertEquals("Document", documentSymbol.name)
        assertEquals("null", documentSymbol.defaultValue())
        assertEquals(true, documentSymbol.isNullable)
        assertEquals(RuntimeTypes.Core.Content.Document.namespace, documentSymbol.namespace)
        assertEquals(1, documentSymbol.dependencies.size)
    }

    @Test
    fun `structures references inner collection members`() {
        // lists/sets reference the inner member, ensure that struct members
        // also contain a reference to the collection member in addition to the
        // collection itself
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
        val structShape = model.expectShape<StructureShape>("foo.bar#MyStruct")
        val structSymbol = provider.toSymbol(structShape)
        // should get reference to List and Instant
        assertEquals(2, structSymbol.references.size)
    }

    @Test
    fun `creates timestamps`() {
        val model = "timestamp MyTimestamp".prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val tsShape = model.expectShape<TimestampShape>("foo.bar#MyTimestamp")
        val timestampSymbol = provider.toSymbol(tsShape)
        assertEquals("$RUNTIME_ROOT_NS.time", timestampSymbol.namespace)
        assertEquals("Instant", timestampSymbol.name)
        assertEquals(1, timestampSymbol.dependencies.size)
    }

    @Test
    fun `it handles recursive structures`() {
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

        val struct1 = model.expectShape<StructureShape>("foo.bar#MyStruct1")
        val structSymbol = provider.toSymbol(struct1)
        assertEquals("foo.bar.model", structSymbol.namespace)
        assertEquals("MyStruct1", structSymbol.name)
        assertEquals("MyStruct1.kt", structSymbol.definitionFile)
        assertEquals(2, structSymbol.references.size)
    }

    @Test
    fun `it specializes event stream members`() {
        // streaming members can only be referenced by top level operation input or output structs
        val model = """
            operation GetEventStream {
                output: GetEventStreamResponse
            }
            
            @streaming
            union Events {
                a: Event1,
                b: Event2,
                c: Event3,
            }
            
            structure Event1{}
            structure Event2{}
            structure Event3{}
            
            structure GetEventStreamResponse {
                events: Events
            }
            
        """.prependNamespaceAndService(operations = listOf("GetEventStream")).toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model)

        // we have to use the synthetic operation input/output to actually trigger the detection of an event stream
        // since it looks for operation input/output shapes
        val streamMember = model.expectShape<MemberShape>("$SYNTHETIC_NAMESPACE.test#GetEventStreamResponse\$events")
        val symbol = provider.toSymbol(streamMember)

        assertEquals("", symbol.namespace)
        assertEquals("null", symbol.defaultValue())
        assertEquals(true, symbol.isNullable)
        assertEquals("Flow<com.test.model.Events>", symbol.name)

        assertEquals("com.test.model.Events", symbol.references[0].symbol.fullName)
    }

    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @ValueSource(strings = ["CLIENT_CAREFUL", "CLIENT"])
    fun `it handles client nullability for IDL v2 check modes`(rawCheckMode: String) {
        val model = """
            operation TestOp {
                input: OpInput
            }

            @input
            structure OpInput {
                @httpHeader("x-test")
                xTestHeader: String,

                @required
                boolean: Boolean

                list: IntList,
                map: StringMap,

                @required
                top: MyStruct
            }

            integer MyInt
            
            @default("foo")
            string MyString

            list IntList {
                member: MyInt
            }

            @sparse
            list SparseIntList {
                member: MyInt
            }

            map StringMap {
                key: String,
                value: MyInt
            }

            structure MyStruct {
                @required
                union: MyUnion,

                @required
                string: String,

                @required
                list: IntList,

                sparseList: SparseIntList,

                @required
                nested: Nested,

                @required
                @clientOptional
                clientOptionalString: String,

                @required
                enum: MyEnum,
                
                @default(1)
                defaultInt: MyInt,
                
                @default("foo")
                defaultString: MyString,
                
                @default(null)
                defaultButNullString: MyString
            }

            enum MyEnum {
                Variant1,
                Variant2
            }

            structure Nested {
                nestedString: String
            }

            union MyUnion {
                blob: Blob,
                boolean: Boolean,
                date: Timestamp,
                int: Integer,
            }
        """.prependNamespaceAndService(operations = listOf("TestOp")).toSmithyModel()

        val checkMode = CheckMode.valueOf(rawCheckMode)
        val settings = model.defaultSettings().copy(api = ApiSettings(nullabilityCheckMode = checkMode))
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, settings = settings)

        // opInput members always optional because of @input
        val opInputStruct = model.expectShape<StructureShape>("com.test#OpInput")
        opInputStruct.members().forEach {
            assertTrue(provider.toSymbol(it).isNullable, "expected $it to be nullable because its marked with @input trait")
        }

        // struct/union members optional in client careful
        val myStruct = model.expectShape<StructureShape>("com.test#MyStruct")
        val unionAndStructMembers = listOf("union", "nested").map { myStruct.getMember(it).get() }
        unionAndStructMembers.forEach {
            val memberSymbol = provider.toSymbol(it)
            when (checkMode) {
                CheckMode.CLIENT_CAREFUL -> assertTrue(memberSymbol.isNullable, "struct/union $it should be optional in $checkMode mode")
                else -> assertFalse(memberSymbol.isNullable, "struct/union $it should be required in $checkMode")
            }
        }

        // required members not optional - except client careful
        myStruct.members()
            .filter(MemberShape::isRequired)
            .filterNot { it in unionAndStructMembers }
            .forEach {
                val memberSymbol = provider.toSymbol(it)
                if (it.hasTrait<ClientOptionalTrait>()) {
                    assertTrue(memberSymbol.isNullable, "@clientOptional member $it should be nullable regardless of @required")
                } else {
                    assertFalse(memberSymbol.isNullable, "@required member $it should not be nullable")
                }
            }

        // union members are not optional
        val unionShape = model.expectShape<UnionShape>("com.test#MyUnion")
        assertTrue(unionShape.members().map { provider.toSymbol(it) }.none { it.isNullable }, "union members should not be nullable")

        // default null are optional
        myStruct.members()
            .filter { it.hasNullDefault() }
            .forEach {
                val memberSymbol = provider.toSymbol(it)
                assertTrue(memberSymbol.isNullable, "member $it with explicit null default should be nullable")
            }

        // non-null default are not optional
        myStruct.members()
            .filter { it.hasNonNullDefault() }
            .forEach {
                val memberSymbol = provider.toSymbol(it)
                assertFalse(memberSymbol.isNullable, "member $it with non-null default should not be nullable")
            }
    }
}
