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
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGeneratorTest {

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

    @Test
    fun `it deserializes a structure with primitive values`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                fooUnion: PrimitiveUnion
            }
            
            union PrimitiveUnion {
                i32: Integer,
                stringA: String
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                when(findNextFieldIndex()) {
                    I32_DESCRIPTOR.index -> value = deserializeInt().let { PrimitiveUnion.I32(it) }
                    STRINGA_DESCRIPTOR.index -> value = deserializeString().let { PrimitiveUnion.StringA(it) }
                    else -> skipValue()
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure with collection types`() {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payloadAggregateUnion: MyAggregateUnion
            }
            
            union MyAggregateUnion {
                intList: IntList,
                intMap: IntMap,
                nested3: Nested,
                @timestampFormat("date-time")
                timestamp4: Timestamp
            }
            
            list IntList {
                member: Integer
            }
            
            map IntMap {
                key: String,
                value: Integer
            }
            
            structure Nested {
                member1: String,
                member2: String
            }
                
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                when(findNextFieldIndex()) {
                    INTLIST_DESCRIPTOR.index -> value =
                        deserializer.deserializeList(INTLIST_DESCRIPTOR) {
                            val col0 = mutableListOf<Int>()
                            while (hasNextElement()) {
                                val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                col0.add(el0)
                            }
                            MyAggregateUnion.IntList(col0)
                        }
                    INTMAP_DESCRIPTOR.index -> value =
                        deserializer.deserializeMap(INTMAP_DESCRIPTOR) {
                            val map0 = mutableMapOf<String, Int>()
                            while (hasNextEntry()) {
                                val k0 = key()
                                val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                map0[k0] = v0
                            }
                            MyAggregateUnion.IntMap(map0)
                        }
                    NESTED3_DESCRIPTOR.index -> value = NestedDeserializer().deserialize(deserializer).let { MyAggregateUnion.Nested3(it) }
                    TIMESTAMP4_DESCRIPTOR.index -> value = deserializeString().let { Instant.fromIso8601(it) }.let { MyAggregateUnion.Timestamp4(it) }
                    else -> skipValue()
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a structure with nested collection types`() {
        val model = """            
            namespace com.test

            use aws.protocols#awsJson1_1
            
            @awsJson1_1
            service Example {
                version: "1.0.0",
                operations: [UnionTestOperation]
            }
            
            @http(method: "GET", uri: "/input/union2")
            operation UnionTestOperation {
                output: NestedListResponse
            }
            
            structure NestedListResponse {
                payloadAggregateUnion: MyAggregateUnion
            }
            
            list IntList {
                member: Integer
            }
            
            list ListOfIntList {
                member: IntList
            }
            
            map MapOfLists {
                key: String,
                value: IntList
            }
            
            union MyAggregateUnion {
                i32: Integer,
                intList: IntList,
                listOfIntList: ListOfIntList,
                mapOfLists: MapOfLists
            }  
        """.asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                when(findNextFieldIndex()) {
                    I32_DESCRIPTOR.index -> value = deserializeInt().let { MyAggregateUnion.I32(it) }
                    INTLIST_DESCRIPTOR.index -> value =
                        deserializer.deserializeList(INTLIST_DESCRIPTOR) {
                            val col0 = mutableListOf<Int>()
                            while (hasNextElement()) {
                                val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                col0.add(el0)
                            }
                            MyAggregateUnion.IntList(col0)
                        }
                    LISTOFINTLIST_DESCRIPTOR.index -> value =
                        deserializer.deserializeList(LISTOFINTLIST_DESCRIPTOR) {
                            val col0 = mutableListOf<List<Int>>()
                            while (hasNextElement()) {
                                val el0 = deserializer.deserializeList(LISTOFINTLIST_C0_DESCRIPTOR) {
                                    val col1 = mutableListOf<Int>()
                                    while (hasNextElement()) {
                                        val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                        col1.add(el1)
                                    }
                                    MyAggregateUnion.ListOfIntList(col1)
                                }
                                col0.add(el0)
                            }
                            MyAggregateUnion.ListOfIntList(col0)
                        }
                    MAPOFLISTS_DESCRIPTOR.index -> value =
                        deserializer.deserializeMap(MAPOFLISTS_DESCRIPTOR) {
                            val map0 = mutableMapOf<String, List<Int>>()
                            while (hasNextEntry()) {
                                val k0 = key()
                                val v0 = deserializer.deserializeList(MAPOFLISTS_C0_DESCRIPTOR) {
                                    val col1 = mutableListOf<Int>()
                                    while (hasNextElement()) {
                                        val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                        col1.add(el1)
                                    }
                                    MyAggregateUnion.MapOfLists(col1)
                                }
                                map0[k0] = v0
                            }
                            MyAggregateUnion.MapOfLists(map0)
                        }
                    else -> skipValue()
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#UnionTestOperation")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()
        val op = ctx.expectShape(shapeId)

        return testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeUnionGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }
}
