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
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

class SerializeUnionGeneratorTest {
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

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()

        val testMembers = when (val shape = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))) {
            is OperationShape -> {
                val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
                val requestBindings = bindingIndex.getRequestBindings(shape)
                val unionShape = ctx.generationCtx.model.expectShape(requestBindings.values.first().member.target)
                unionShape.members().toList()
            }
            is StructureShape -> {
                shape.members().toList()
            }
            else -> throw RuntimeException("unknown conversion for $shapeId")
        }

        return testRender(testMembers) { members, writer ->
            SerializeUnionGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is FooUnion.IntVal -> field(INTVAL_DESCRIPTOR, input.value)
                    is FooUnion.StrVal -> field(STRVAL_DESCRIPTOR, input.value)
                    is FooUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
            ).asSmithyModel()

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
                            input.value.forEach { (key, value) -> mapEntry(key, STRMAPMAPVAL_C0_DESCRIPTOR) {
                                value.forEach { (key1, value1) -> entry(key1, value1) }
                            }}
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

        val actual = getContentsForShape(model, "com.test#Foo")

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is MyAggregateUnion.Nested3 -> field(NESTED3_DESCRIPTOR, NestedDocumentSerializer(input.value))
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

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
            ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                when (input) {
                    is FooUnion.StrMapVal -> {
                        mapField(STRMAPVAL_DESCRIPTOR) {
                            input.value.forEach { (key, value) -> entry(key, BarStructDocumentSerializer(value)) }
                        }
                    }
                    is FooUnion.StructListVal -> {
                        listField(STRUCTLISTVAL_DESCRIPTOR) {
                            for (el0 in input.value) {
                                serializeSdkSerializable(BarStructDocumentSerializer(el0))
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }
}
