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
import java.lang.RuntimeException
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.integration.SerializeStructGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class SerializeStructGeneratorTest {
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
                val bindingIndex = ctx.generationCtx.model.getKnowledge(HttpBindingIndex::class.java)
                val requestBindings = bindingIndex.getRequestBindings(shape)
                requestBindings.values
                    .filter { it.location == HttpBinding.Location.DOCUMENT }
                    .sortedBy { it.memberName }
                    .map { it.member }
            }
            is StructureShape -> {
                shape.members().toList()
            }
            else -> throw RuntimeException("unknown conversion for $shapeId")
        }

        SerializeStructGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()

        return writer.toString()
    }

    @Test
    fun `it handles smoke test request serializer`() {
        val contents = getContentsForShape("com.test#SmokeTest")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct {
    input.payload1?.let { field(PAYLOAD1_DESCRIPTOR, it) }
    input.payload2?.let { field(PAYLOAD2_DESCRIPTOR, it) }
    input.payload3?.let { field(PAYLOAD3_DESCRIPTOR, NestedSerializer(it)) }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it handles list inputs`() {
        val contents = getContentsForShape("com.test#ListInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct {
    if (input.blobList != null) {
        listField(BLOBLIST_DESCRIPTOR) {
            for(m0 in input.blobList) {
                serializeString(m0.encodeBase64String())
            }
        }
    }
    if (input.enumList != null) {
        listField(ENUMLIST_DESCRIPTOR) {
            for(m0 in input.enumList) {
                serializeString(m0.value)
            }
        }
    }
    if (input.intList != null) {
        listField(INTLIST_DESCRIPTOR) {
            for(m0 in input.intList) {
                serializeInt(m0)
            }
        }
    }
    if (input.nestedIntList != null) {
        listField(NESTEDINTLIST_DESCRIPTOR) {
            for(m0 in input.nestedIntList) {
                serializer.serializeList {
                    for(m1 in m0) {
                        serializeInt(m1)
                    }
                }
            }
        }
    }
    if (input.structList != null) {
        listField(STRUCTLIST_DESCRIPTOR) {
            for(m0 in input.structList) {
                serializeSdkSerializable(NestedSerializer(m0))
            }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it handles maps`() {
        val contents = getContentsForShape("com.test#MapInput")
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
serializer.serializeStruct {
    if (input.blobMap != null) {
        mapField(BLOBMAP_DESCRIPTOR) {
            input.blobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
        }
    }
    if (input.enumMap != null) {
        mapField(ENUMMAP_DESCRIPTOR) {
            input.enumMap.forEach { (key, value) -> entry(key, value.value) }
        }
    }
    if (input.intMap != null) {
        mapField(INTMAP_DESCRIPTOR) {
            input.intMap.forEach { (key, value) -> entry(key, value) }
        }
    }
    if (input.structMap != null) {
        mapField(STRUCTMAP_DESCRIPTOR) {
            input.structMap.forEach { (key, value) -> entry(key, ReachableOnlyThroughMapSerializer(value)) }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes enums as raw values`() {
        val contents = getContentsForShape("com.test#NestedEnum")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct {
    input.myEnum?.let { field(MYENUM_DESCRIPTOR, it.value) }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
