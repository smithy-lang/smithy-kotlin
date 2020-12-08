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
import software.amazon.smithy.kotlin.codegen.integration.DeserializeUnionGenerator
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGeneratorTest {
    private val defaultModel = javaClass.getResource("http-binding-protocol-generator-test.smithy")

    @Test
    fun `it handles collections of collection types`() {
        val ctx = javaClass.getResource("http-binding-nested-union-model.smithy").asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#UnionTestOperation")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeUnionGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    when(findNextFieldIndex()) {
        I32_DESCRIPTOR.index -> value = deserializeInt()?.let { MyAggregateUnion.I32(it) }
        INTLIST_DESCRIPTOR.index -> value =
            deserializer.deserializeList(INTLIST_DESCRIPTOR) {
                val list0 = mutableListOf<Int>()
                while(hasNextElement()) {
                    val el0 = deserializeInt()
                    if (el0 != null) list0.add(el0)
                }
                MyAggregateUnion.IntList(list0)
            }
        LISTOFINTLIST_DESCRIPTOR.index -> value =
            deserializer.deserializeList(LISTOFINTLIST_DESCRIPTOR) {
                val list0 = mutableListOf<List<Int>>()
                while(hasNextElement()) {
                    val el0 =
                    deserializer.deserializeList(LISTOFINTLIST_C0_DESCRIPTOR) {
                        val list1 = mutableListOf<Int>()
                        while(hasNextElement()) {
                            val el1 = deserializeInt()
                            if (el1 != null) list1.add(el1)
                        }
                        MyAggregateUnion.ListOfIntList(list1)
                    }
                    if (el0 != null) list0.add(el0)
                }
                MyAggregateUnion.ListOfIntList(list0)
            }
        MAPOFLISTS_DESCRIPTOR.index -> value =
            deserializer.deserializeMap(MAPOFLISTS_DESCRIPTOR) {
                val map0 = mutableMapOf<String, List<Int>>()
                while(hasNextEntry()) {
                    val k0 = key()
                    val el0 =
                    deserializer.deserializeList(MAPOFLISTS_C0_DESCRIPTOR) {
                        val list1 = mutableListOf<Int>()
                        while(hasNextElement()) {
                            val el1 = deserializeInt()
                            if (el1 != null) list1.add(el1)
                        }
                        MyAggregateUnion.MapOfLists(list1)
                    }
                    if (el0 != null) map0[k0] = el0
                }
                MyAggregateUnion.MapOfLists(map0)
            }
        else -> skipValue()
    }
}
"""
        // kotlin.test.assertEquals(expected, contents)
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles union deserializer with primitive subtypes`() {
        val ctx = defaultModel.asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#UnionOutput")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeUnionGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    when(findNextFieldIndex()) {
        I32_DESCRIPTOR.index -> value = deserializeInt()?.let { MyUnion.I32(it) }
        STRINGA_DESCRIPTOR.index -> value = deserializeString()?.let { MyUnion.StringA(it) }
        else -> skipValue()
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles union deserializer with aggregate subtypes`() {
        val ctx = defaultModel.asSmithy().newTestContext()
        val op = ctx.expectShape("com.test#UnionAggregateOutput")

        val contents = testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeUnionGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    when(findNextFieldIndex()) {
        I32_DESCRIPTOR.index -> value = deserializeInt()?.let { MyAggregateUnion.I32(it) }
        INTLIST_DESCRIPTOR.index -> value =
            deserializer.deserializeList(INTLIST_DESCRIPTOR) {
                val list0 = mutableListOf<Int>()
                while(hasNextElement()) {
                    val el0 = deserializeInt()
                    if (el0 != null) list0.add(el0)
                }
                MyAggregateUnion.IntList(list0)
            }
        INTMAP_DESCRIPTOR.index -> value =
            deserializer.deserializeMap(INTMAP_DESCRIPTOR) {
                val map0 = mutableMapOf<String, Int>()
                while(hasNextEntry()) {
                    val k0 = key()
                    val el0 = deserializeInt()
                    if (el0 != null) map0[k0] = el0
                }
                MyAggregateUnion.IntMap(map0)
            }
        NESTED3_DESCRIPTOR.index -> value = NestedDeserializer().deserialize(deserializer)?.let { MyAggregateUnion.Nested3(it) }
        TIMESTAMP4_DESCRIPTOR.index -> value = deserializeString()?.let { Instant.fromIso8601(it) }?.let { MyAggregateUnion.Timestamp4(it) }
        else -> skipValue()
    }
}
"""
        // kotlin.test.assertEquals(expected, contents)
        contents.shouldContainOnlyOnce(expected)
    }
}
