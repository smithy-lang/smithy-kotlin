/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.kotlin.codegen.test.*
import kotlin.test.Test

class DeserializeStructGeneratorTest {
    private val modelPrefix = """
            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                output: FooResponse
            }        
    """.prependNamespaceAndService(
        protocol = AwsProtocolModelDeclaration.REST_JSON,
        operations = listOf("Foo"),
    ).trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger", "BigDecimal"])
    fun `it deserializes a structure with a simple fields`(memberType: String) {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: $memberType
            }
        """
            ).toSmithyModel()

        val kotlinTypeFromSmithyType = when (memberType) {
            "Integer" -> "Int"
            else -> memberType
        }

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserialize$kotlinTypeFromSmithyType()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure with a timestamp field`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeInstant(TimestampFormat.EPOCH_SECONDS)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure with a list of timestamp values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: TimestampList
            }
            
            list TimestampList {
                member: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Instant>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeInstant(TimestampFormat.EPOCH_SECONDS) } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure with a list of document values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: DocumentList
            }
            
            list DocumentList {
                member: Document
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Document>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeDocument() } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
        actual.shouldContainOnlyOnceWithDiff("import aws.smithy.kotlin.runtime.content.Document")
    }

    @Test
    fun `it deserializes a structure with a nested structure`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: NestedStructure
            }
            
            structure NestedStructure {
                nestedPayload: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeNestedStructureDocument(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a union of primitives`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeUnionStructureDocument(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: IntList
            }
            
            list IntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Int>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: IntMap
            }
            
            map IntMap {
                key: String,
                value: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a union of primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<FooUnion>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeFooUnionDocument(deserializer) } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: SparseIntList
            }
            
            @sparse
            list SparseIntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Int?>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull() }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse list of a struct`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: SparseIntList
            }
            
            @sparse
            list SparseIntList {
                member: SparseListElement
            }
            
            structure SparseListElement {
                bar: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<SparseListElement?>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeSparseListElementDocument(deserializer) } else { deserializeNull() }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a nested list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<List<String>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<String>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a nested structure containing a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<NestedStructure>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeNestedStructureDocument(deserializer) } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a nested list of a nested list of a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<List<List<Boolean>>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<List<Boolean>>()
                                        while (hasNextElement()) {
                                            val el1 = deserializer.deserializeList(PAYLOAD_C1_DESCRIPTOR) {
                                                val col2 = mutableListOf<Boolean>()
                                                while (hasNextElement()) {
                                                    val el2 = if (nextHasValue()) { deserializeBoolean() } else { deserializeNull(); continue }
                                                    col2.add(el2)
                                                }
                                                col2
                                            }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of unique primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: IntegerSet
            }
            
            @uniqueItems
            list IntegerSet {
                member: Integer
            }            
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Int>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of unique maps`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: MapSet
            }
            
            @uniqueItems
            list MapSet {
                member: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Map<String, String>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                        val map1 = mutableMapOf<String, String>()
                                        while (hasNextEntry()) {
                                            val k1 = key()
                                            val v1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                            map1[k1] = v1
                                        }
                                        map1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of unique lists of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: ListSet
            }
            
            @uniqueItems
            list ListSet {
                member: StringList
            }
            
            list StringList {
                member: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<List<String>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<String>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a nested structure of a list of unique primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeFooStructDocument(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a list of unique primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<List<Int>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<Int>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a list of unique primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<Int>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<Int>()
                                                while (hasNextElement()) {
                                                    val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                                    col1.add(el1)
                                                }
                                                col1
                                            }
                                        } else { deserializeNull(); continue }
            
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse map of a list of unique primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<Int>?>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<Int>()
                                                while (hasNextElement()) {
                                                    val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                                    col1.add(el1)
                                                }
                                                col1
                                            }
                                        } else { deserializeNull() }
            
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a map of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Map<String, Boolean>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                        val map1 = mutableMapOf<String, Boolean>()
                                        while (hasNextEntry()) {
                                            val k1 = key()
                                            val v1 = if (nextHasValue()) { deserializeBoolean() } else { deserializeNull(); continue }
                                            map1[k1] = v1
                                        }
                                        map1
                                    }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payload: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, String>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map with an enum key`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<KeyType, String>()
                                while (hasNextEntry()) {
                                    val k0 = KeyType.fromValue(key())
                                    val v0 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map with an enum key and a value that is a map with an enum key`() {
        val model = (
            modelPrefix + """
            structure FooResponse {
                payload: OuterKeyValuedMap
            }

            map OuterKeyValuedMap {
                key: OuterKeyType,
                value: InnerKeyValuedMap
            }

            @enum([
                {
                    value: "FOO",
                },
                {
                    value: "BAR",
                },
            ])
            string OuterKeyType

            map InnerKeyValuedMap {
                key: InnerKeyType,
                value: String
            }

            @enum([
                {
                    value: "BAZ",
                },
                {
                    value: "QUX",
                },
            ])
            string InnerKeyType
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<OuterKeyType, Map<InnerKeyType, String>>()
                                while (hasNextEntry()) {
                                    val k0 = OuterKeyType.fromValue(key())
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                                val map1 = mutableMapOf<InnerKeyType, String>()
                                                while (hasNextEntry()) {
                                                    val k1 = InnerKeyType.fromValue(key())
                                                    val v1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                                    map1[k1] = v1
                                                }
                                                map1
                                            }
                                        } else { deserializeNull(); continue }

                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a union of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooUnion>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeFooUnionDocument(deserializer) } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, String?>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeString() } else { deserializeNull() }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse map of a nested structure`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooStruct?>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeFooStructDocument(deserializer) } else { deserializeNull() }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a list of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<String>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<String>()
                                                while (hasNextElement()) {
                                                    val el1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                                    col1.add(el1)
                                                }
                                                col1
                                            }
                                        } else { deserializeNull(); continue }
            
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a list of a map of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<Map<String, Boolean>>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<Map<String, Boolean>>()
                                                while (hasNextElement()) {
                                                    val el1 = deserializer.deserializeMap(PAYLOAD_C1_DESCRIPTOR) {
                                                        val map2 = mutableMapOf<String, Boolean>()
                                                        while (hasNextEntry()) {
                                                            val k2 = key()
                                                            val v2 = if (nextHasValue()) { deserializeBoolean() } else { deserializeNull(); continue }
                                                            map2[k2] = v2
                                                        }
                                                        map2
                                                    }
                                                    col1.add(el1)
                                                }
                                                col1
                                            }
                                        } else { deserializeNull(); continue }
            
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a value structure containing a primitive type`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooStructure>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeFooStructureDocument(deserializer) } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while (true) {
        when (findNextFieldIndex()) {
            PAYLOAD_DESCRIPTOR.index -> builder.payload =
                deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Map<String, Int>>()
                    while (hasNextEntry()) {
                        val k0 = key()
                        val v0 =
                            if (nextHasValue()) {
                                deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                    val map1 = mutableMapOf<String, Int>()
                                    while (hasNextEntry()) {
                                        val k1 = key()
                                        val v1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                        map1[k1] = v1
                                    }
                                    map1
                                }
                            } else { deserializeNull(); continue }

                        map0[k0] = v0
                    }
                    map0
                }
            null -> break@loop
            else -> skipValue()
        }
    }
}
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a sparse map of a map of a primitive value`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while (true) {
        when (findNextFieldIndex()) {
            PAYLOAD_DESCRIPTOR.index -> builder.payload =
                deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Map<String, Int>?>()
                    while (hasNextEntry()) {
                        val k0 = key()
                        val v0 =
                            if (nextHasValue()) {
                                deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                    val map1 = mutableMapOf<String, Int>()
                                    while (hasNextEntry()) {
                                        val k1 = key()
                                        val v1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                        map1[k1] = v1
                                    }
                                    map1
                                }
                            } else { deserializeNull() }

                        map0[k0] = v0
                    }
                    map0
                }
            null -> break@loop
            else -> skipValue()
        }
    }
}
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing an enum string`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FIRSTENUM_DESCRIPTOR.index -> builder.firstEnum = deserializeString().let { SimpleYesNo.fromValue(it) }
                        SECONDENUM_DESCRIPTOR.index -> builder.secondEnum = deserializeString().let { TypedYesNo.fromValue(it) }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of enum strings`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<SimpleYesNo>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeString().let { SimpleYesNo.fromValue(it) } } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of enum string values`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
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
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, SimpleYesNo>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeString().let { SimpleYesNo.fromValue(it) } } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing an int enum`() {
        val model = (
            modelPrefix + """
            structure FooResponse {
                firstEnum: SimpleYesNo,
            }

            intEnum SimpleYesNo {
                YES = 1
                NO = 2
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FIRSTENUM_DESCRIPTOR.index -> builder.firstEnum = deserializeInt().let { SimpleYesNo.fromValue(it) }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a blob`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooBlob: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOB_DESCRIPTOR.index -> builder.fooBlob = deserializeByteArray()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    // TODO ~ this codegen path is not exercised by protocol tests
    @Test
    fun `it deserializes a structure containing a structure`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooStructVal: FooStruct
            }
            
            structure FooStruct {
                strVal: String
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOSTRUCTVAL_DESCRIPTOR.index -> builder.fooStructVal = deserializeFooStructDocument(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of value type blob`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooBlobMap: BlobMap
            }
            
            map BlobMap {
                key: String,
                value: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOBMAP_DESCRIPTOR.index -> builder.fooBlobMap =
                            deserializer.deserializeMap(FOOBLOBMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, ByteArray>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeByteArray() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of value type blob`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooBlobList: BlobList
            }
            
            list BlobList {                
                member: Blob
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOBLIST_DESCRIPTOR.index -> builder.fooBlobList =
                            deserializer.deserializeList(FOOBLOBLIST_DESCRIPTOR) {
                                val col0 = mutableListOf<ByteArray>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeByteArray() } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                col0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a timestamp`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooTime: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOTIME_DESCRIPTOR.index -> builder.fooTime = deserializeInstant(TimestampFormat.EPOCH_SECONDS)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of value type timestamp`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                fooTimestampMap: TimestampMap
            }
            
            map TimestampMap {
                key: String,
                value: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOTIMESTAMPMAP_DESCRIPTOR.index -> builder.fooTimestampMap =
                            deserializer.deserializeMap(FOOTIMESTAMPMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Instant>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInstant(TimestampFormat.EPOCH_SECONDS) } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = codegenDeserializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }
}
