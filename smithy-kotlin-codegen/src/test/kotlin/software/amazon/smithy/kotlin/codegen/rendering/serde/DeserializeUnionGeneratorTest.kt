/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGeneratorTest {

    private val modelPrefix = """
            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                output: FooResponse
            }        
    """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.RestJson, operations = listOf("Foo")).trimIndent()

    @Test
    fun `it deserializes a union with primitive values`() {
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
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        I32_DESCRIPTOR.index -> value = PrimitiveUnion.I32(deserializeInt())
                        STRINGA_DESCRIPTOR.index -> value = PrimitiveUnion.Stringa(deserializeString())
                        null -> break@loop
                        else -> value = PrimitiveUnion.SdkUnknown.also { skipValue() }
                    }
                }
            }
        """.formatForTest("")

        val actual = model.codegenDeserializer("PrimitiveUnion")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a union containing collections of itself`() {
        val model = (
            modelPrefix + """            
            structure FooResponse { 
                payloadAggregateUnion: MyAggregateUnion
            }
            
            union MyAggregateUnion {
                unionList: MyAggregateUnionList,
                unionMap: MyAggregateUnionMap,
            }
            
            list MyAggregateUnionList {
                member: MyAggregateUnion
            }
            
            map MyAggregateUnionMap {
                key: String,
                value: MyAggregateUnion
            }                
        """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        UNIONLIST_DESCRIPTOR.index -> value =
                            deserializer.deserializeList(UNIONLIST_DESCRIPTOR) {
                                val col0 = mutableListOf<MyAggregateUnion>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeMyAggregateUnionDocument(deserializer) } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                MyAggregateUnion.UnionList(col0)
                            }
                        UNIONMAP_DESCRIPTOR.index -> value =
                            deserializer.deserializeMap(UNIONMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, MyAggregateUnion>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeMyAggregateUnionDocument(deserializer) } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                MyAggregateUnion.UnionMap(map0)
                            }
                        null -> break@loop
                        else -> value = MyAggregateUnion.SdkUnknown.also { skipValue() }
                    }
                }
            }
        """.formatForTest("")

        val actual = model.codegenDeserializer("MyAggregateUnion")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a union of collection types containing collections`() {
        val model = (
            modelPrefix + """            
                    structure FooResponse { 
                        payload: FooUnion
                    }
                    
                    union FooUnion {
                        intListVal: IntList,
                        strMapVal: BarStructMap
                    }
                    
                    list IntList {
                        member: Integer
                    }
                    
                    map BarStructMap {
                        key: String,
                        value: BarStructList
                    }                
                    
                    list BarStructList {
                        member: Bar2StructMap
                    }
                    
                    map Bar2StructMap {
                        key: String,
                        value: BarUnion
                    }
                    
                    union BarUnion {
                        i32: IntStruct,
                        stringA: StringStruct
                    }
                    
                    structure StringStruct {
                        member: String
                    }
                    
                    structure IntStruct {
                        member: Integer
                    }
                """
            ).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        INTLISTVAL_DESCRIPTOR.index -> value =
                            deserializer.deserializeList(INTLISTVAL_DESCRIPTOR) {
                                val col0 = mutableListOf<Int>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    col0.add(el0)
                                }
                                FooUnion.IntListVal(col0)
                            }
                        STRMAPVAL_DESCRIPTOR.index -> value =
                            deserializer.deserializeMap(STRMAPVAL_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, List<Map<String, BarUnion>>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(STRMAPVAL_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<Map<String, BarUnion>>()
                                                while (hasNextElement()) {
                                                    val el1 = deserializer.deserializeMap(STRMAPVAL_C1_DESCRIPTOR) {
                                                        val map2 = mutableMapOf<String, BarUnion>()
                                                        while (hasNextEntry()) {
                                                            val k2 = key()
                                                            val v2 = if (nextHasValue()) { deserializeBarUnionDocument(deserializer) } else { deserializeNull(); continue }
                                                            map2[k2] = v2
                                                        }
                                                        FooUnion.StrMapVal(map2)
                                                    }
                                                    col1.add(el1)
                                                }
                                                FooUnion.StrMapVal(col1)
                                            }
                                        } else { deserializeNull(); continue }
            
                                    map0[k0] = v0
                                }
                                FooUnion.StrMapVal(map0)
                            }
                        null -> break@loop
                        else -> value = FooUnion.SdkUnknown.also { skipValue() }
                    }
                }
            }

        """.formatForTest("")

        val actual = model.codegenDeserializer("FooUnion")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it deserializes a union with nested collection types`() {
        val model = """                       
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
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.AwsJson1_1, operations = listOf("UnionTestOperation")).toSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        I32_DESCRIPTOR.index -> value = MyAggregateUnion.I32(deserializeInt())
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
                                    val v0 =
                                        if (nextHasValue()) {
                                            deserializer.deserializeList(MAPOFLISTS_C0_DESCRIPTOR) {
                                                val col1 = mutableListOf<Int>()
                                                while (hasNextElement()) {
                                                    val el1 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                                    col1.add(el1)
                                                }
                                                MyAggregateUnion.MapOfLists(col1)
                                            }
                                        } else { deserializeNull(); continue }
            
                                    map0[k0] = v0
                                }
                                MyAggregateUnion.MapOfLists(map0)
                            }
                        null -> break@loop
                        else -> value = MyAggregateUnion.SdkUnknown.also { skipValue() }
                    }
                }
            }
        """.formatForTest("")

        val actual = model.codegenDeserializer("MyAggregateUnion")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    private fun Model.codegenDeserializer(shapeName: String): String {
        val ctx = newTestContext()

        val unionShape = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#$shapeName"))
        val testMembers = unionShape.members().toList()

        return testRender(testMembers) { members, writer ->
            DeserializeUnionGenerator(
                ctx.generationCtx,
                shapeName,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }
}
