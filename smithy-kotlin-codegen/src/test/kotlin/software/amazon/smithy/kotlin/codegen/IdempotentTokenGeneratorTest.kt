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
import software.amazon.smithy.kotlin.codegen.integration.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class IdempotentTokenGeneratorTest {
    val model: Model = Model.assembler()
        .addImport(javaClass.getResource("idempotent-token-test-model.smithy"))
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
        val ctx = ProtocolGenerator.GenerationContext(settings, model, service, provider, listOf(), generator.protocol, delegator)
        return TestContext(ctx, manifest, generator)
    }

    private fun getTransformFileContents(filename: String): String {
        val (ctx, manifest, generator) = newTestContext()
        generator.generateSerializers(ctx)
        generator.generateDeserializers(ctx)
        ctx.delegator.flushWriters()
        return getTransformFileContents(manifest, filename)
    }

    private fun getTransformFileContents(manifest: MockManifest, filename: String): String {
        return manifest.expectFileString("src/main/kotlin/test/transform/$filename")
    }

    @Test
    fun `it serializes operation inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class AllocateWidgetSerializer(val input: AllocateWidgetInput) : HttpSerialize {

    companion object {
        private val CLIENTTOKEN_DESCRIPTOR = SdkFieldDescriptor("clientToken", SerialKind.String)
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(CLIENTTOKEN_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/AllocateWidget"
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

        val serializer = serializationContext.serializationProvider()
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            input.clientToken?.let { field(CLIENTTOKEN_DESCRIPTOR, it) } ?: field(CLIENTTOKEN_DESCRIPTOR, idempotencyTokenProvider.invoke())
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes operation query inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetQuerySerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class AllocateWidgetQuerySerializer(val input: AllocateWidgetInputQuery) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/AllocateWidgetQuery"
            parameters {
                append("clientToken", (input.clientToken ?: serializationContext.idempotencyTokenProvider.invoke()))
            }
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes operation header inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetHeaderSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class AllocateWidgetSerializer(val input: AllocateWidgetInput) : HttpSerialize {

    companion object {
        private val CLIENTTOKEN_DESCRIPTOR = SdkFieldDescriptor("clientToken", SerialKind.String)
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(CLIENTTOKEN_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, serializationContext: SerializationContext) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/AllocateWidget"
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

        val serializer = serializationContext.serializationProvider()
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            input.clientToken?.let { field(CLIENTTOKEN_DESCRIPTOR, it) } ?: field(CLIENTTOKEN_DESCRIPTOR, idempotencyTokenProvider.invoke())
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
