/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.kotlin.codegen.DefaultValueSerializationMode
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.knowledge.NullableIndex
import kotlin.test.Test

class SerializeStructGeneratorTest {
    private val modelPrefix = """
            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooRequest
            }        
    """.prependNamespaceAndService(
        protocol = AwsProtocolModelDeclaration.REST_JSON,
        operations = listOf("Foo"),
    ).trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger", "BigDecimal"])
    fun `it serializes a structure with a simple fields`(memberType: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "String, \"foo\", \"foo\"",
        "Boolean, false, false",
        "Byte, 0, 0.toByte()",
        "Short, 0, 0.toShort()",
        "Integer, 2, 2",
        "Long, 3, 3L",
        "Float, 0, 0f",
        "Double, 0, 0.0",
    )
    fun `it serializes default values only when different mode`(memberType: String, modelDefault: String, defaultValue: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @default($modelDefault)
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != $defaultValue) field(PAYLOAD_DESCRIPTOR, input.payload)
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "String, \"foo\"",
        "Boolean, false",
        "Byte, 0",
        "Short, 0",
        "Integer, 2",
        "Long, 3",
        "Float, 0",
        "Double, 0",
    )
    fun `it always serializes default values using always mode`(memberType: String, modelDefault: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @default($modelDefault)
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                field(PAYLOAD_DESCRIPTOR, input.payload)
            }
        """.trimIndent()

        val settings = model.defaultSettings(defaultValueSerializationMode = DefaultValueSerializationMode.ALWAYS)
        val actual = codegenSerializerForShape(model, "com.test#Foo", settings = settings)
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "String, \"foo\"",
        "Boolean, false",
        "Byte, 0",
        "Short, 0",
        "Integer, 2",
        "Long, 3",
        "Float, 0",
        "Double, 0",
    )
    fun `it always serializes required default values`(memberType: String, modelDefault: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                @default($modelDefault)
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                field(PAYLOAD_DESCRIPTOR, input.payload)
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger", "BigDecimal"])
    fun `it serializes a structure with simple required fields`(memberType: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                field(PAYLOAD_DESCRIPTOR, input.payload)
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @CsvSource(
        "EPOCH_SECONDS, epoch-seconds",
        "ISO_8601, date-time",
        "RFC_5322, http-date",
    )
    fun `it serializes a structure with timestamp field`(runtimeEnum: String, tsFormatTrait: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @timestampFormat("$tsFormatTrait")
                payload: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it, TimestampFormat.$runtimeEnum) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with required timestamp field`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                payload: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                field(PAYLOAD_DESCRIPTOR, input.payload, TimestampFormat.EPOCH_SECONDS)
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a document field`() {
        val model = (
            modelPrefix + """            
            structure FooRequest {
                payload: Document
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a list of epoch timestamp values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: TimestampList
            }
            
            list TimestampList {
                member: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeInstant(el0, TimestampFormat.EPOCH_SECONDS)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a list of iso8601 timestamp members`() {
        // timestamp format on the member itself
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: TimestampList
            }
            
            list TimestampList {
                @timestampFormat("date-time")
                member: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeInstant(el0, TimestampFormat.ISO_8601)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a list of iso8601 timestamp values`() {
        // timestamp format on the value type

        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: TimestampList
            }
            
            @timestampFormat("date-time")
            timestamp CustomTimestamp
            
            list TimestampList {
                member: CustomTimestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeInstant(el0, TimestampFormat.ISO_8601)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a nested structure`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: NestedStructure
            }
            
            structure NestedStructure {
                nestedPayload: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it, ::serializeNestedStructureDocument) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a nested structure using client mode`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                payload: NestedStructure
            }
            
            structure NestedStructure {
                nestedPayload: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                field(PAYLOAD_DESCRIPTOR, input.payload, ::serializeNestedStructureDocument)
            }
        """.trimIndent()

        val settings = model.defaultSettings(nullabilityCheckMode = NullableIndex.CheckMode.CLIENT)
        val actual = codegenSerializerForShape(model, "com.test#Foo", settings = settings)
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a union of primitives`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: UnionStructure
            }
            
            union UnionStructure {
                    intValue: Integer,
                    stringValue: String,
                    booleanValue: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it, ::serializeUnionStructureDocument) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: IntList
            }
            
            list IntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeInt(el0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a required list`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                payload: IntList
            }
            
            list IntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                listField(PAYLOAD_DESCRIPTOR) {
                    for (el0 in input.payload) {
                        serializeInt(el0)
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a list of primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: ListOfList
            }
            
            list ListOfList {
                member: StringList
            }
            
            list StringList {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in el0) {
                                    serializeString(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a union of primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: UnionList
            }
            
            list UnionList {
                member: FooUnion
            }
            
            union FooUnion {
                strval: String,
                intval: Integer,
                boolval: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeSdkSerializable(asSdkSerializable(el0, ::serializeFooUnionDocument))
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparseIntList
            }
            
            @sparse
            list SparseIntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            if (el0 != null) serializeInt(el0) else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of structures`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparseStructureList
            }
            
            @sparse
            list SparseStructureList {
                member: FooStruct
            }
            
            structure FooStruct {
                fooValue: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            if (el0 != null) {
                                serializeSdkSerializable(asSdkSerializable(el0, ::serializeFooStructDocument))
                            } else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of timestamps`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparseTimestampList
            }
            
            @sparse
            list SparseTimestampList {
                member: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            if (el0 != null) {
                                serializeInstant(el0, TimestampFormat.EPOCH_SECONDS)
                            } else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of lists`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparsePrimitiveListList
            }
            
            @sparse
            list SparsePrimitiveListList {
                member: PrimitiveList
            }
            
            list PrimitiveList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                if (el0 != null) {
                                    for (el1 in el0) {
                                        serializeInt(el1)
                                    }
                                } else serializeNull()
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of maps`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparseMapList
            }
            
            @sparse
            list SparseMapList {
                member: PrimitiveMap
            }
            
            map PrimitiveMap {
                key: String,
                value: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                if (el0 != null) {
                                    el0.forEach { (key1, value1) ->
                                        entry(key1, value1)
                                    }
                                } else serializeNull()
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse list of blobs`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: SparseBlobList
            }
            
            @sparse
            list SparseBlobList {
                member: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            if (el0 != null) serializeByteArray(el0) else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a nested list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringList
            }
            
            list StringList {
                member: StringList2
            }
            
            list StringList2 {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in el0) {
                                    serializeString(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a nested structure containing a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringList
            }
            
            list StringList {
                member: NestedStructure
            }
            
            structure NestedStructure {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeSdkSerializable(asSdkSerializable(el0, ::serializeNestedStructureDocument))
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a nested list of a nested list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: BooleanList
            }
            
            list BooleanList {
                member: BooleanList2
            }
            
            list BooleanList2 {
                member: BooleanList3
            }
            
            list BooleanList3 {
                member: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in el0) {
                                    serializer.serializeList(PAYLOAD_C1_DESCRIPTOR) {
                                        for (el2 in el1) {
                                            serializeBoolean(el2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: IntegerSet
            }
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }            
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeInt(el0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of a map primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: IntegerSet
            }
            
            @uniqueItems
            list IntegerSet {
                member: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                el0.forEach { (key1, value1) ->
                                    entry(key1, value1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of a list primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: IntegerSet
            }
            
            @uniqueItems
            list IntegerSet {
                member: StringList
            }
            
            list StringList {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in el0) {
                                    serializeString(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a nested structure of a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: FooStruct
            }
            
            structure FooStruct {
                payload: IntegerSet
            }            
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it, ::serializeFooStructDocument) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: ListOfSet
            }
            
            list ListOfSet {
                member: IntegerSet
            }            
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in el0) {
                                    serializeInt(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: MapOfSet
            }
            
            map MapOfSet {
                key: String,
                value: IntegerSet
            }            
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in value) {
                                    serializeInt(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: MapOfSet
            }
            
            @sparse
            map MapOfSet {
                key: String,
                value: IntegerSet
            }            
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                    for (el1 in value) {
                                        serializeInt(el1)
                                    }
                                }
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of a map of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: ListOfBooleanMap
            }
            
            list ListOfBooleanMap {
                member: BooleanMap
            }
            
            map BooleanMap {
                key: String,
                value: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                el0.forEach { (key1, value1) ->
                                    entry(key1, value1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            entry(key, value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map with enum keys`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: KeyValuedMap
            }
            
            map KeyValuedMap {
                key: KeyType,
                value: String
            }
            
            @enum([
                {
                    value: "FOO",
                },
                {
                    value: "BAR",
                },
            ])
            string KeyType
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            entry(key.value, value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a required map`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                @required
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                mapField(PAYLOAD_DESCRIPTOR) {
                    input.payload.forEach { (key, value) ->
                        entry(key, value)
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a union of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: FooUnion
            }
            
            union FooUnion {
                strval: String,
                intval: Integer,
                boolval: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, asSdkSerializable(value, ::serializeFooUnionDocument)) }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            @sparse
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                entry(key, value)
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a blob value`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: BlobMap
            }

            @sparse
            map BlobMap {
                key: String,
                value: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                entry(key, value)
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a timestamp value`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: TimestampMap
            }

            @sparse
            map TimestampMap {
                key: String,
                value: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                entry(key, it, TimestampFormat.EPOCH_SECONDS)
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a nested structure`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            @sparse
            map StringMap {
                key: String,
                value: FooStruct
            }
            
            structure FooStruct {
                fooValue: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> if (value != null) entry(key, asSdkSerializable(value, ::serializeFooStructDocument)) else entry(key, null as String?) }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a list of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: StringList
            }
            
            list StringList {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in value) {
                                    serializeString(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a list of a map of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: ListOfMapMap
            }
            
            map ListOfMapMap {
                key: String,
                value: ListOfMap
            }
            
            list ListOfMap {
                member: BooleanMap
            }
            
            map BooleanMap {
                key: String,
                value: Boolean
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                for (el1 in value) {
                                    serializer.serializeMap(PAYLOAD_C1_DESCRIPTOR) {
                                        el1.forEach { (key2, value2) ->
                                            entry(key2, value2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a value structure containing a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: FooStructure
            }
            
            structure FooStructure {
                nestedPayload: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, asSdkSerializable(value, ::serializeFooStructureDocument)) }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: StringMap2
            }
            
            map StringMap2 {
                key: String,
                value: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            mapEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                value.forEach { (key1, value1) ->
                                    entry(key1, value1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a sparse map of a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: StringMap
            }
            
            @sparse
            map StringMap {
                key: String,
                value: StringMap2
            }
            
            map StringMap2 {
                key: String,
                value: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                mapEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                                    value.forEach { (key1, value1) ->
                                        entry(key1, value1)
                                    }
                                }
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing an enum string`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                firstEnum: SimpleYesNo,
                secondEnum: TypedYesNo
            }
            
            @enum([{value: "YES"}, {value: "NO"}])
            string SimpleYesNo

            @enum([{value: "Yes", name: "YES"}, {value: "No", name: "NO"}])
            string TypedYesNo
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.firstEnum?.let { field(FIRSTENUM_DESCRIPTOR, it.value) }
                input.secondEnum?.let { field(SECONDENUM_DESCRIPTOR, it.value) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of enum strings`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: EnumList               
            }
            
            list EnumList {
                member: SimpleYesNo
            }
            
            @enum([{value: "YES"}, {value: "NO"}])
            string SimpleYesNo
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (el0 in input.payload) {
                            serializeString(el0.value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of enum string values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: EnumMap               
            }
            
            map EnumMap {
                key: String,
                value: SimpleYesNo
            }
            
            @enum([{value: "YES"}, {value: "NO"}])
            string SimpleYesNo
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) ->
                            entry(key, value.value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing an int enum`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                firstEnum: SimpleYesNo,
            }

            intEnum SimpleYesNo {
                YES = 1
                NO = 2
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.firstEnum?.let { field(FIRSTENUM_DESCRIPTOR, it.value) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a blob`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooBlob: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.fooBlob?.let { field(FOOBLOB_DESCRIPTOR, it.encodeBase64String()) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of value type blob`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooBlobMap: BlobMap
            }
            
            map BlobMap {
                key: String,
                value: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooBlobMap != null) {
                    mapField(FOOBLOBMAP_DESCRIPTOR) {
                        input.fooBlobMap.forEach { (key, value) ->
                            entry(key, value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a list of value type blob`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooBlobList: BlobList
            }
            
            list BlobList {                
                member: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooBlobList != null) {
                    listField(FOOBLOBLIST_DESCRIPTOR) {
                        for (el0 in input.fooBlobList) {
                            serializeByteArray(el0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing timestamps`() {
        // ensure we read the timestamp trait correctly off members and target shape
        val model = (
            modelPrefix + """            
                
            @timestampFormat("date-time")
            timestamp CustomTimestamp
            
            structure FooRequest { 
                member1: Timestamp,
                
                @timestampFormat("http-date")
                member2: Timestamp,
                
                member3: CustomTimestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.member1?.let { field(MEMBER1_DESCRIPTOR, it, TimestampFormat.EPOCH_SECONDS) }
                input.member2?.let { field(MEMBER2_DESCRIPTOR, it, TimestampFormat.RFC_5322) }
                input.member3?.let { field(MEMBER3_DESCRIPTOR, it, TimestampFormat.ISO_8601) }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of value type epoch timestamp`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooTimestampMap: TimestampMap
            }
            
            map TimestampMap {
                key: String,
                value: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooTimestampMap != null) {
                    mapField(FOOTIMESTAMPMAP_DESCRIPTOR) {
                        input.fooTimestampMap.forEach { (key, value) ->
                            entry(key, it, TimestampFormat.EPOCH_SECONDS)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map member iso8601 timestamp`() {
        // :test(member > timestamp)
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooTimestampMap: TimestampMap
            }
            
            map TimestampMap {
                key: String,
                @timestampFormat("date-time")
                value: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooTimestampMap != null) {
                    mapField(FOOTIMESTAMPMAP_DESCRIPTOR) {
                        input.fooTimestampMap.forEach { (key, value) ->
                            entry(key, it, TimestampFormat.ISO_8601)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map value of type iso8601 timestamp`() {
        // :test(timestamp)
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooTimestampMap: TimestampMap
            }
            @timestampFormat("date-time")
            timestamp CustomTimestamp
            
            map TimestampMap {
                key: String,
                value: CustomTimestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooTimestampMap != null) {
                    mapField(FOOTIMESTAMPMAP_DESCRIPTOR) {
                        input.fooTimestampMap.forEach { (key, value) ->
                            entry(key, it, TimestampFormat.ISO_8601)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }
}
