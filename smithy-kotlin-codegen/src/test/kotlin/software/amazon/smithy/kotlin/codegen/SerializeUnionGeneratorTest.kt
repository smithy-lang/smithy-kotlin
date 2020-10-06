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
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.integration.SerializeUnionGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

class SerializeUnionGeneratorTest {
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

    /**
     * Get the contents for the given shape ID which should either be
     * an operation shape or a structure shape. In the case of an operation shape
     * the members bound to the document of the request shape for the operation
     * will be returned
     */
    private fun getContentsForShape(shapeId: String): String {
        val ctx = newTestContext()
        val writer = KotlinWriter("test")
        val shape = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))

        val members = when (shape) {
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

        SerializeUnionGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        return writer.toString()
    }

    @Test
    fun `it handles union request serializer`() {
        val contents = getContentsForShape("com.test#UnionInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct(OBJ_DESCRIPTOR) {
    when (input) {
        is MyUnion.I32 -> field(I32_DESCRIPTOR, input.value)
        is MyUnion.StringA -> field(STRINGA_DESCRIPTOR, input.value)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it handles list inputs`() {
        val contents = getContentsForShape("com.test#UnionAggregateInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct(OBJ_DESCRIPTOR) {
    when (input) {
        is MyAggregateUnion.I32 -> field(I32_DESCRIPTOR, input.value)
        is MyAggregateUnion.IntList -> {
            listField(INTLIST_DESCRIPTOR) {
                for(m0 in input.value) {
                    serializeInt(m0)
                }
            }
        }
        is MyAggregateUnion.IntMap -> {
            mapField(INTMAP_DESCRIPTOR) {
                input.value.forEach { (key, value) -> entry(key, value) }
            }
        }
        is MyAggregateUnion.Nested3 -> field(NESTED3_DESCRIPTOR, NestedSerializer(input.value))
        is MyAggregateUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
