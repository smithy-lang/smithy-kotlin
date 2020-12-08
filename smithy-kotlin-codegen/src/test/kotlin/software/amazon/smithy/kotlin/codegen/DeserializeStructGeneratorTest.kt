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
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.integration.DeserializeStructGenerator
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeStructGeneratorTest {
    private val defaultModelResource = javaClass.getResource("http-binding-protocol-generator-test.smithy")

    @Test
    fun `it handles smoke test deserializer`() {
        val ctx = defaultModelResource.asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#SmokeTest")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while(true) {
        when(findNextFieldIndex()) {
            PAYLOAD1_DESCRIPTOR.index -> builder.payload1 = deserializeString()
            PAYLOAD2_DESCRIPTOR.index -> builder.payload2 = deserializeInt()
            PAYLOAD3_DESCRIPTOR.index -> builder.payload3 = NestedDeserializer().deserialize(deserializer)
            PAYLOAD4_DESCRIPTOR.index -> builder.payload4 = deserializeString()?.let { Instant.fromIso8601(it) }
            null -> break@loop
            else -> skipValue()
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles non-boxed primitives`() {
        val ctx = javaClass.getResource("unboxed-primitives-test.smithy").asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#UnboxedPrimitivesTest")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
            PAYLOAD1_DESCRIPTOR.index -> builder.payload1 = deserializeInt() ?: 0
            PAYLOAD2_DESCRIPTOR.index -> builder.payload2 = deserializeBool() ?: false
            PAYLOAD3_DESCRIPTOR.index -> builder.payload3 = deserializeByte() ?: 0
            PAYLOAD4_DESCRIPTOR.index -> builder.payload4 = deserializeShort() ?: 0
            PAYLOAD5_DESCRIPTOR.index -> builder.payload5 = deserializeLong() ?: 0
            PAYLOAD6_DESCRIPTOR.index -> builder.payload6 = deserializeFloat() ?: 0.0f
            PAYLOAD7_DESCRIPTOR.index -> builder.payload7 = deserializeDouble() ?: 0.0
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles list outputs`() {
        val ctx = defaultModelResource.asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#ListInput")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while(true) {
        when(findNextFieldIndex()) {
            BLOBLIST_DESCRIPTOR.index -> builder.blobList =
                deserializer.deserializeList(BLOBLIST_DESCRIPTOR) {
                    val list0 = mutableListOf<ByteArray>()
                    while(hasNextElement()) {
                        val el0 = deserializeString()?.decodeBase64Bytes()
                        if (el0 != null) list0.add(el0)
                    }
                    list0
                }
            ENUMLIST_DESCRIPTOR.index -> builder.enumList =
                deserializer.deserializeList(ENUMLIST_DESCRIPTOR) {
                    val list0 = mutableListOf<MyEnum>()
                    while(hasNextElement()) {
                        val el0 = deserializeString()?.let { MyEnum.fromValue(it) }
                        if (el0 != null) list0.add(el0)
                    }
                    list0
                }
            INTLIST_DESCRIPTOR.index -> builder.intList =
                deserializer.deserializeList(INTLIST_DESCRIPTOR) {
                    val list0 = mutableListOf<Int>()
                    while(hasNextElement()) {
                        val el0 = deserializeInt()
                        if (el0 != null) list0.add(el0)
                    }
                    list0
                }
            NESTEDINTLIST_DESCRIPTOR.index -> builder.nestedIntList =
                deserializer.deserializeList(NESTEDINTLIST_DESCRIPTOR) {
                    val list0 = mutableListOf<List<Int>>()
                    while(hasNextElement()) {
                        val el0 =
                        deserializer.deserializeList(NESTEDINTLIST_DESCRIPTOR) {
                            val list1 = mutableListOf<Int>()
                            while(hasNextElement()) {
                                val el1 = deserializeInt()
                                if (el1 != null) list1.add(el1)
                            }
                            list1
                        }
                        if (el0 != null) list0.add(el0)
                    }
                    list0
                }
            STRUCTLIST_DESCRIPTOR.index -> builder.structList =
                deserializer.deserializeList(STRUCTLIST_DESCRIPTOR) {
                    val list0 = mutableListOf<Nested>()
                    while(hasNextElement()) {
                        val el0 = NestedDeserializer().deserialize(deserializer)
                        if (el0 != null) list0.add(el0)
                    }
                    list0
                }
            null -> break@loop
            else -> skipValue()
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles map outputs`() {
        val ctx = defaultModelResource.asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#MapInput")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while(true) {
        when(findNextFieldIndex()) {
            BLOBMAP_DESCRIPTOR.index -> builder.blobMap =
                deserializer.deserializeMap(BLOBMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, ByteArray>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeString()?.decodeBase64Bytes()
                        if (el0 != null) map0[k0] = el0
                    }
                    map0
                }
            ENUMMAP_DESCRIPTOR.index -> builder.enumMap =
                deserializer.deserializeMap(ENUMMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, MyEnum>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeString()?.let { MyEnum.fromValue(it) }
                        if (el0 != null) map0[k0] = el0
                    }
                    map0
                }
            INTMAP_DESCRIPTOR.index -> builder.intMap =
                deserializer.deserializeMap(INTMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Int>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeInt()
                        if (el0 != null) map0[k0] = el0
                    }
                    map0
                }
            NESTEDMAP_DESCRIPTOR.index -> builder.nestedMap =
                deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Map<String, Int>>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 =
                        deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                            val map1 = mutableMapOf<String, Int>()
                            while(hasNextEntry()) {
                                val k1 = key()
                                val el1 = deserializeInt()
                                if (el1 != null) map1[k1] = el1
                            }
                            map1
                        }
                        if (el0 != null) map0[k0] = el0
                    }
                    map0
                }
            STRUCTMAP_DESCRIPTOR.index -> builder.structMap =
                deserializer.deserializeMap(STRUCTMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, ReachableOnlyThroughMap>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = ReachableOnlyThroughMapDeserializer().deserialize(deserializer)
                        if (el0 != null) map0[k0] = el0
                    }
                    map0
                }
            null -> break@loop
            else -> skipValue()
        }
    }
}
"""
        // kotlin.test.assertEquals(expected, contents)
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles sparse lists`() {
        val expected = """
            // Code generated by smithy-kotlin-codegen. DO NOT EDIT!

            package test



            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        SPARSEINTLIST_DESCRIPTOR.index -> builder.sparseIntList =
                            deserializer.deserializeList(SPARSEINTLIST_DESCRIPTOR) {
                                val list0 = mutableListOf<Int?>()
                                while(hasNextElement()) {
                                    val el0 = deserializeInt()
                                    list0.add(el0)
                                }
                                list0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        
        """.trimIndent()

        val ctx = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                output: GetFooOutput
            }
            
            @sparse
            list SparseIntList {
                member: Integer
            }
            
            structure GetFooOutput {
                sparseIntList: SparseIntList
            }
        """.trimIndent()
            .asSmithyModel()
            .newTestContext()

        val op = ctx.expectShape("com.test#GetFoo")

        val actual = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        kotlin.test.assertEquals(expected, actual)
    }

    @Test
    fun `it handles sparse maps`() {
        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        SPARSEINTMAP_DESCRIPTOR.index -> builder.sparseIntMap =
                            deserializer.deserializeMap(SPARSEINTMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Int?>()
                                while(hasNextEntry()) {
                                    val k0 = key()
                                    val el0 = deserializeInt()
                                    map0[k0] = el0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val ctx = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                output: GetFooOutput
            }
            
            @sparse
            map SparseIntMap {
                key: String,
                value: Integer
            }
            
            structure GetFooOutput {
                sparseIntMap: SparseIntMap
            }
        """.trimIndent()
            .asSmithyModel()
            .newTestContext()

        val op = ctx.expectShape("com.test#GetFoo")

        val actual = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        // kotlin.test.assertEquals(expected, actual)
        actual.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles maps`() {
        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        INTMAP_DESCRIPTOR.index -> builder.intMap =
                            deserializer.deserializeMap(INTMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Int>()
                                while(hasNextEntry()) {
                                    val k0 = key()
                                    val el0 = deserializeInt()
                                    if (el0 != null) map0[k0] = el0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val ctx = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                output: GetFooOutput
            }
            
            map IntMap {
                key: String,
                value: Integer
            }
            
            structure GetFooOutput {
                intMap: IntMap
            }
        """.trimIndent()
            .asSmithyModel()
            .newTestContext()

        val op = ctx.expectShape("com.test#GetFoo")

        val actual = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        // kotlin.test.assertEquals(expected, actual)
        actual.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles nested maps`() {
        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        NESTEDMAP_DESCRIPTOR.index -> builder.nestedMap =
                            deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Map<String, Int>>()
                                while(hasNextEntry()) {
                                    val k0 = key()
                                    val el0 =
                                    deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                                        val map1 = mutableMapOf<String, Int>()
                                        while(hasNextEntry()) {
                                            val k1 = key()
                                            val el1 = deserializeInt()
                                            if (el1 != null) map1[k1] = el1
                                        }
                                        map1
                                    }
                                    if (el0 != null) map0[k0] = el0
                                }
                                map0
                            }
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val ctx = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                output: GetFooOutput
            }
            
            map NestedMap {
                key: String,
                value: IntMap
            }
            
            map IntMap {
                key: String,
                value: Integer
            }
            
            structure GetFooOutput {
                nestedMap: NestedMap
            }
        """.trimIndent()
            .asSmithyModel()
            .newTestContext()

        val op = ctx.expectShape("com.test#GetFoo")

        val actual = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        //kotlin.test.assertEquals(expected, actual)
        actual.shouldContainOnlyOnce(expected)
    }
}
