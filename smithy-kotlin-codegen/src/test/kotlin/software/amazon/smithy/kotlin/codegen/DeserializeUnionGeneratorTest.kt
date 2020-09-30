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
import software.amazon.smithy.kotlin.codegen.integration.DeserializeUnionGenerator
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeUnionGeneratorTest {
    val model: Model = Model.assembler()
        .addImport(javaClass.getResource("http-binding-protocol-generator-test.smithy"))
        .discoverModels()
        .assemble()
        .unwrap()

    data class TestContext(val generationCtx: ProtocolGenerator.GenerationContext, val manifest: MockManifest, val generator: MockHttpProtocolGenerator)

    private fun newTestContext(): TestContext {
        val settings = KotlinSettings.from(model, Node.objectNodeBuilder()
            .withMember("module", Node.from("test"))
            .withMember("moduleVersion", Node.from("1.0.0"))
            .build())
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
            delegator)
        return TestContext(ctx, manifest, generator)
    }

    @Test
    fun `it handles union deserializer`() {
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
        PAYLOADUNION_DESCRIPTOR.index -> value = MyUnionDeserializer().deserialize(deserializer)
        else -> skipValue()
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

}
