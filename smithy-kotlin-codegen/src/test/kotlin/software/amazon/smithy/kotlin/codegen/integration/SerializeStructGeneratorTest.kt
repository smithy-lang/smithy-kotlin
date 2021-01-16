/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class SerializeStructGeneratorTest2 {
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
                input: FooRequest
            }        
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double"/*, "BigInteger", "BigDecimal"*/])
    // TODO ~ Support BigInteger and BigDecimal Types
    fun `it serializes a structure with a simple fields`(memberType: String) {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: $memberType
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, it) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a timestamp field`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: Timestamp
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { rawField(PAYLOAD_DESCRIPTOR, it.format(TimestampFormat.EPOCH_SECONDS)) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure with a list of timestamp values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: TimestampList
            }
            
            list TimestampList {
                member: Timestamp
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeRaw(c0.format(TimestampFormat.EPOCH_SECONDS))
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    // TODO ~ Support Document Type

    @Test
    fun `it serializes a structure with containing a structure`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: NestedStructure
            }
            
            structure NestedStructure {
                nestedPayload: String
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, NestedStructureSerializer(it)) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, UnionStructureSerializer(it)) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeInt(c0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeSdkSerializable(FooUnionSerializer(c0))
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            if (c0 != null) serializeInt(c0) else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (c1 in c0) {
                                    serializeString(c1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeSdkSerializable(NestedStructureSerializer(c0))
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (c1 in c0) {
                                    serializer.serializeList(PAYLOAD_C1_DESCRIPTOR) {
                                        for (c2 in c1) {
                                            serializeBoolean(c2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: IntegerSet
            }
            
            set IntegerSet {
                member: Integer
            }            
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeInt(c0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of a map primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
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
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                c0.forEach { (key1, value1) -> entry(key1, value1) }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a set of a list primitive values`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
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
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (c1 in c0) {
                                    serializeString(c1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            
            set IntegerSet {
                member: Integer
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload?.let { field(PAYLOAD_DESCRIPTOR, FooStructSerializer(it)) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            
            set IntegerSet {
                member: Integer
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                for (c1 in c0) {
                                    serializeInt(c1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            
            set IntegerSet {
                member: Integer
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                            for (c1 in value) {
                                serializeInt(c1)
                            }
                        }}
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializer.serializeMap(PAYLOAD_C0_DESCRIPTOR) {
                                c0.forEach { (key1, value1) -> entry(key1, value1) }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, value) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, FooUnionSerializer(value)) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, value) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> if (value != null) entry(key, FooStructSerializer(value)) else entry(key, null as String?) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                            for (c1 in value) {
                                serializeString(c1)
                            }
                        }}
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> listEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                            for (c1 in value) {
                                serializer.serializeMap(PAYLOAD_C1_DESCRIPTOR) {
                                    c1.forEach { (key2, value2) -> entry(key2, value2) }
                                }
                            }
                        }}
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, FooStructureSerializer(value)) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> mapEntry(key, PAYLOAD_C0_DESCRIPTOR) {
                            value.forEach { (key1, value1) -> entry(key1, value1) }
                        }}
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.firstEnum?.let { field(FIRSTENUM_DESCRIPTOR, it.value) }
                input.secondEnum?.let { field(SECONDENUM_DESCRIPTOR, it.value) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeString(c0.value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR) {
                        input.payload.forEach { (key, value) -> entry(key, value.value) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.fooBlob?.let { field(FOOBLOB_DESCRIPTOR, it.encodeBase64String()) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooBlobMap != null) {
                    mapField(FOOBLOBMAP_DESCRIPTOR) {
                        input.fooBlobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooBlobList != null) {
                    listField(FOOBLOBLIST_DESCRIPTOR) {
                        for (c0 in input.fooBlobList) {
                            serializeString(c0.encodeBase64String())
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a timestamp`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                fooTime: Timestamp
            }
        """
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.fooTime?.let { rawField(FOOTIME_DESCRIPTOR, it.format(TimestampFormat.EPOCH_SECONDS)) }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a map of value type timestamp`() {
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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.fooTimestampMap != null) {
                    mapField(FOOTIMESTAMPMAP_DESCRIPTOR) {
                        input.fooTimestampMap.forEach { (key, value) -> rawEntry(key, it.format(TimestampFormat.EPOCH_SECONDS)) }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()

        val op = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))
        return testRender(ctx.requestMembers(op)) { members, writer ->
            SerializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }
}

/**
 * // Code generated by smithy-kotlin-codegen. DO NOT EDIT!
 * package test
 */
private fun String.stripCodegenPrefix() =
    this.substring(this.indexOf("package test") + "package test".length).trim()
