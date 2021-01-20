/*
 *
 *  * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  * SPDX-License-Identifier: Apache-2.0.
 *
 */

package software.amazon.smithy.kotlin.codegen.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeStructGeneratorTest {
    private val modelPrefix = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    Foo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                output: FooResponse
            }        
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double"/*, "BigInteger", "BigDecimal"*/])
    // TODO ~ Support BigInteger and BigDecimal Types
    fun `it deserializes a structure with a simple fields`(memberType: String) {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: $memberType
            }
        """
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserializeString().let { Instant.fromEpochSeconds(it) }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Instant>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeString().let { Instant.fromEpochSeconds(it) } } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = NestedStructureDeserializer().deserialize(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = UnionStructureDeserializer().deserialize(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<FooUnion>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { FooUnionDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<NestedStructure>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { NestedStructureDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a set of primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: IntegerSet
            }
            
            set IntegerSet {
                member: Integer
            }            
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableSetOf<Int>()
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a set of a map primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: IntegerSet
            }
            
            set IntegerSet {
                member: StringMap
            }
            
            map StringMap {
                key: String,
                value: String
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableSetOf<Map<String, String>>()
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a set of a list of primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: IntegerSet
            }
            
            set IntegerSet {
                member: StringList
            }
            
            list StringList {
                member: String
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableSetOf<List<String>>()
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a nested structure of a set of primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: FooStruct
            }
            
            structure FooStruct {
                payload: IntegerSet
            }            
            
            set IntegerSet {
                member: Integer
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = FooStructDeserializer().deserialize(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a list of a set of primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: ListOfSet
            }
            
            list ListOfSet {
                member: IntegerSet
            }            
            
            set IntegerSet {
                member: Integer
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeList(PAYLOAD_DESCRIPTOR) {
                                val col0 = mutableListOf<Set<Int>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableSetOf<Int>()
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure containing a map of a set of primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: MapOfSet
            }
            
            map MapOfSet {
                key: String,
                value: IntegerSet
            }            
            
            set IntegerSet {
                member: Integer
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Set<Int>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableSetOf<Int>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooUnion>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { FooUnionDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooStruct?>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { FooStructDeserializer().deserialize(deserializer) } else { deserializeNull() }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<String>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<String>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                            col1.add(el1)
                                        }
                                        col1
                                    }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<Map<String, Boolean>>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = deserializer.deserializeList(PAYLOAD_C0_DESCRIPTOR) {
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, FooStructure>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { FooStructureDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload =
                            deserializer.deserializeMap(PAYLOAD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Map<String, Int>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = deserializer.deserializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                        val map1 = mutableMapOf<String, Int>()
                                        while (hasNextEntry()) {
                                            val k1 = key()
                                            val v1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                            map1[k1] = v1
                                        }
                                        map1
                                    }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOB_DESCRIPTOR.index -> builder.fooBlob = deserializeString().decodeBase64Bytes()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }


    @Test
    // TODO ~ this codegen path is not exercised by protocol tests
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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOSTRUCTVAL_DESCRIPTOR.index -> builder.fooStructVal = FooStructDeserializer().deserialize(deserializer)
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOBMAP_DESCRIPTOR.index -> builder.fooBlobMap =
                            deserializer.deserializeMap(FOOBLOBMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, ByteArray>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeString().decodeBase64Bytes() } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOBLOBLIST_DESCRIPTOR.index -> builder.fooBlobList =
                            deserializer.deserializeList(FOOBLOBLIST_DESCRIPTOR) {
                                val col0 = mutableListOf<ByteArray>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeString().decodeBase64Bytes() } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOTIME_DESCRIPTOR.index -> builder.fooTime = deserializeString().let { Instant.fromEpochSeconds(it) }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        FOOTIMESTAMPMAP_DESCRIPTOR.index -> builder.fooTimestampMap =
                            deserializer.deserializeMap(FOOTIMESTAMPMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Instant>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeString().let { Instant.fromEpochSeconds(it) } } else { deserializeNull(); continue }
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

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    // TODO - test to render a list/map w/ nested structure

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()
        val op = ctx.expectShape(shapeId)

        return testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }
}