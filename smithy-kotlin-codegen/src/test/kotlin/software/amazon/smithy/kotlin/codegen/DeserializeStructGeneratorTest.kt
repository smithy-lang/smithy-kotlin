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
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.DeserializeStructGenerator
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeStructGeneratorTest {
    val defaultModel: Model = Model.assembler()
        .addImport(javaClass.getResource("http-binding-protocol-generator-test.smithy"))
        .discoverModels()
        .assemble()
        .unwrap()

    data class TestContext(val generationCtx: ProtocolGenerator.GenerationContext, val manifest: MockManifest, val generator: MockHttpProtocolGenerator)

    private fun newTestContext(testModel: Model = defaultModel): TestContext {
        val settings = KotlinSettings.from(
            testModel,
            Node.objectNodeBuilder()
                .withMember("module", Node.from("test"))
                .withMember("moduleVersion", Node.from("1.0.0"))
                .build()
        )
        val manifest = MockManifest()
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(testModel, "test")
        val service = testModel.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val delegator = KotlinDelegator(settings, testModel, manifest, provider)
        val generator = MockHttpProtocolGenerator()
        val ctx = ProtocolGenerator.GenerationContext(
            settings,
            testModel,
            service,
            provider,
            listOf(),
            generator.protocol,
            delegator
        )
        return TestContext(ctx, manifest, generator)
    }

    @Test
    fun `it handles smoke test deserializer`() {
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#SmokeTest"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val documentMembers = responseBindings.values
            .filter { it.location == HttpBinding.Location.DOCUMENT }
            .sortedBy { it.memberName }
            .map { it.member }

        DeserializeStructGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
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
        val model = Model.assembler()
            .addImport(javaClass.getResource("unboxed-primitives-test.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()
        val ctx = newTestContext(model)
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#UnboxedPrimitivesTest"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val documentMembers = responseBindings.values
            .filter { it.location == HttpBinding.Location.DOCUMENT }
            .sortedBy { it.memberName }
            .map { it.member }

        DeserializeStructGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
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
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#ListInput"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val documentMembers = responseBindings.values
            .filter { it.location == HttpBinding.Location.DOCUMENT }
            .sortedBy { it.memberName }
            .map { it.member }

        DeserializeStructGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
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
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#MapInput"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val documentMembers = responseBindings.values
            .filter { it.location == HttpBinding.Location.DOCUMENT }
            .sortedBy { it.memberName }
            .map { it.member }

        DeserializeStructGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
        val expected = """
deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
    loop@while(true) {
        when(findNextFieldIndex()) {
            BLOBMAP_DESCRIPTOR.index -> builder.blobMap =
                deserializer.deserializeMap(BLOBMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, ByteArray?>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeString()?.decodeBase64Bytes()
                        map0[k0] = el0
                    }
                    map0
                }
            ENUMMAP_DESCRIPTOR.index -> builder.enumMap =
                deserializer.deserializeMap(ENUMMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, MyEnum?>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeString()?.let { MyEnum.fromValue(it) }
                        map0[k0] = el0
                    }
                    map0
                }
            INTMAP_DESCRIPTOR.index -> builder.intMap =
                deserializer.deserializeMap(INTMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Int?>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = deserializeInt()
                        map0[k0] = el0
                    }
                    map0
                }
            NESTEDMAP_DESCRIPTOR.index -> builder.nestedMap =
                deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, Map<String, Int?>?>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 =
                        deserializer.deserializeMap(NESTEDMAP_DESCRIPTOR) {
                            val map1 = mutableMapOf<String, Int?>()
                            while(hasNextEntry()) {
                                val k1 = key()
                                val el1 = deserializeInt()
                                map1[k1] = el1
                            }
                            map1
                        }
                        map0[k0] = el0
                    }
                    map0
                }
            STRUCTMAP_DESCRIPTOR.index -> builder.structMap =
                deserializer.deserializeMap(STRUCTMAP_DESCRIPTOR) {
                    val map0 = mutableMapOf<String, ReachableOnlyThroughMap?>()
                    while(hasNextEntry()) {
                        val k0 = key()
                        val el0 = ReachableOnlyThroughMapDeserializer().deserialize(deserializer)
                        map0[k0] = el0
                    }
                    map0
                }
            null -> break@loop
            else -> skipValue()
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }
}
