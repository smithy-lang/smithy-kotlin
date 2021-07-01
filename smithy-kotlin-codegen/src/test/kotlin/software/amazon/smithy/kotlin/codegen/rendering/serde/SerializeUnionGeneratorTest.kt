/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.*

class SerializeUnionGeneratorTest {
    private val modelPrefix = """
            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooRequest
            }        
    """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.RestJson, operations = listOf("Foo")).trimIndent()

    @Test
    fun `it serializes a structure containing a union of primitive types`() {
        val model = (
            modelPrefix + """            
            structure FooRequest { 
                payload: FooUnion
            }
            
            union FooUnion {
                intVal: Integer,
                strVal: String,
                @timestampFormat("date-time")
                timestamp4: Timestamp
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is FooUnion.IntVal -> field(INTVAL_DESCRIPTOR, input.value)
                    is FooUnion.StrVal -> field(STRVAL_DESCRIPTOR, input.value)
                    is FooUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))
                }
            }
        """.trimIndent()

        val actual = codegenUnionSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a union of collection types`() {
        val model = (
            modelPrefix + """            
                    structure FooRequest { 
                        payload: FooUnion
                    }
                    
                    union FooUnion {
                        intListVal: IntList,
                        strMapVal: StringMap,
                        strMapMapVal: MapOfStringMap,
                        intListListVal: ListOfIntList
                    }
                    
                    list IntList {
                        member: Integer
                    }
                    
                    map StringMap {
                        key: String,
                        value: String
                    }
                    
                    list ListOfIntList {
                        member: IntList
                    }
                    
                    map MapOfStringMap {
                        key: String,
                        value: StringMap
                    }
                """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is FooUnion.IntListListVal -> {
                        listField(INTLISTLISTVAL_DESCRIPTOR) {
                            for (el0 in input.value) {
                                serializer.serializeList(INTLISTLISTVAL_C0_DESCRIPTOR) {
                                    for (el1 in el0) {
                                        serializeInt(el1)
                                    }
                                }
                            }
                        }
                    }
                    is FooUnion.IntListVal -> {
                        listField(INTLISTVAL_DESCRIPTOR) {
                            for (el0 in input.value) {
                                serializeInt(el0)
                            }
                        }
                    }
                    is FooUnion.StrMapMapVal -> {
                        mapField(STRMAPMAPVAL_DESCRIPTOR) {
                            input.value.forEach { (key, value) ->
                                mapEntry(key, STRMAPMAPVAL_C0_DESCRIPTOR) {
                                    value.forEach { (key1, value1) -> entry(key1, value1) }
                                }
                            }
                        }
                    }
                    is FooUnion.StrMapVal -> {
                        mapField(STRMAPVAL_DESCRIPTOR) {
                            input.value.forEach { (key, value) -> entry(key, value) }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenUnionSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a union of nested types`() {
        val model = (
            modelPrefix + """            
                    structure FooRequest {
                        payloadAggregateUnion: MyAggregateUnion
                    }
                    
                    union MyAggregateUnion {
                        nested3: Nested                        
                    }
                    
                    structure Nested {
                        member1: String,
                        member2: String
                    }
                """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is MyAggregateUnion.Nested3 -> field(NESTED3_DESCRIPTOR, input.value, ::serializeNestedDocument)
                }
            }
        """.trimIndent()

        val actual = codegenUnionSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a structure containing a union of collection of structures`() {
        val model = (
            modelPrefix + """            
                    structure FooRequest { 
                        payload: FooUnion
                    }
                    
                    union FooUnion {
                        structListVal: BarStructList,
                        strMapVal: StringMap,
                    }
                    
                    list BarStructList {
                        member: BarStruct
                    }
                    
                    map StringMap {
                        key: String,
                        value: BarStruct
                    }
                    
                    structure BarStruct {
                        foo: String,
                        bar: Integer
                    }
                """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is FooUnion.StrMapVal -> {
                        mapField(STRMAPVAL_DESCRIPTOR) {
                            input.value.forEach { (key, value) -> entry(key, asSdkSerializable(value, ::serializeBarStructDocument)) }
                        }
                    }
                    is FooUnion.StructListVal -> {
                        listField(STRUCTLISTVAL_DESCRIPTOR) {
                            for (el0 in input.value) {
                                serializeSdkSerializable(asSdkSerializable(el0, ::serializeBarStructDocument))
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenUnionSerializerForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }
}
