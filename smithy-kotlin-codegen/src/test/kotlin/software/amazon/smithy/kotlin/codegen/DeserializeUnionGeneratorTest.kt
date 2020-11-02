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
import software.amazon.smithy.kotlin.codegen.integration.DeserializeUnionGenerator
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGeneratorTest {
    val model: Model = Model.assembler()
        .addImport(javaClass.getResource("http-binding-protocol-generator-test.smithy"))
        .discoverModels()
        .assemble()
        .unwrap()

    data class TestContext(val generationCtx: ProtocolGenerator.GenerationContext, val manifest: MockManifest, val generator: MockHttpProtocolGenerator)

    private fun newTestContext(): TestContext {
        val settings = KotlinSettings.from(
            model,
            Node.objectNodeBuilder()
                .withMember("module", Node.from("test"))
                .withMember("moduleVersion", Node.from("1.0.0"))
                .build()
        )
        val manifest = MockManifest()
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val delegator = KotlinDelegator(settings, model, manifest, provider)
        val generator = MockHttpProtocolGenerator()
        val ctx = ProtocolGenerator.GenerationContext(
            settings,
            model,
            service,
            provider,
            listOf(),
            generator.protocol,
            delegator
        )
        return TestContext(ctx, manifest, generator)
    }

    @Test
    fun `it handles union deserializer with primitive subtypes`() {
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#UnionOutput"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val documentMembers = responseBindings.values
            .filter { it.location == HttpBinding.Location.DOCUMENT }
            .sortedBy { it.memberName }
            .map { it.member }

        DeserializeUnionGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
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
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val op = ctx.generationCtx.model.expectShape(ShapeId.from("com.test#UnionAggregateOutput"))

        val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
        val responseBindings = bindingIndex.getResponseBindings(op)
        val unionMember = ctx.generationCtx.model.expectShape(responseBindings.values.first().member.target) as UnionShape
        val documentMembers = unionMember.members().toList()

        DeserializeUnionGenerator(
            ctx.generationCtx,
            documentMembers,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        val contents = writer.toString()
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
                val map0 = mutableMapOf<String, Int?>()
                while(hasNextEntry()) {
                    val k0 = key()
                    val el0 = deserializeInt()
                    map0[k0] = el0
                }
                MyAggregateUnion.IntMap(map0)
            }
        NESTED3_DESCRIPTOR.index -> value = NestedDeserializer().deserialize(deserializer)?.let { MyAggregateUnion.Nested3(it) }
        TIMESTAMP4_DESCRIPTOR.index -> value = deserializeString()?.let { Instant.fromIso8601(it) }?.let { MyAggregateUnion.Timestamp4(it) }
        else -> skipValue()
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }
}
