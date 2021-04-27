/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.ext.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId

class ServiceGeneratorTest {
    private val commonTestContents: String

    init {
        commonTestContents = generateService("service-generator-test-operations.smithy")
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import ${TestModelDefault.NAMESPACE}.model.*")
        commonTestContents.shouldContainOnlyOnce("import $CLIENT_RT_ROOT_NS.SdkClient")
    }

    @Test
    fun `it renders interface`() {
        commonTestContents.shouldContainOnlyOnce("interface TestClient : SdkClient {")
    }

    @Test
    fun `it overrides SdkClient serviceName`() {
        val expected = """
            override val serviceName: String
                get() = "Test"
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders signatures correctly`() {
        val expectedSignatures = listOf(
            "suspend fun getFoo(input: GetFooRequest): GetFooResponse",
            "suspend fun getFooNoInput(input: GetFooNoInputRequest): GetFooNoInputResponse",
            "suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse",
            "suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse",
            "suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T",
            "suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T",
            "suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse"
        )
        expectedSignatures.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
            companion object {
                operator fun invoke(block: Config.DslBuilder.() -> Unit = {}): TestClient {
                    val config = Config.BuilderImpl().apply(block).build()
                    return DefaultTestClient(config)
                }
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it generates config`() {
        val expected = "class Config"
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        commonTestContents.assertBalancedBracesAndParens()
    }

    @Test
    fun `it allows overriding defined sections`() {
        val model = javaClass
            .classLoader
            .getResource("software/amazon/smithy/kotlin/codegen/service-generator-test-operations.smithy")!!
            .toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val service = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)
        writer.onSection(SECTION_SERVICE_INTERFACE_COMPANION_OBJ) {
            writer.openBlock("companion object {")
                .write("fun foo(): Int = 1")
                .closeBlock("}")
        }

        writer.onSection(SECTION_SERVICE_INTERFACE_CONFIG) {
            writer.openBlock("class Config {")
                .write("var bar: Int = 2")
                .closeBlock("}")
        }

        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings("test", "0.0"), sdkId = service.id.name)
        val renderingCtx = RenderingContext(writer, service, model, provider, settings)
        val generator = ServiceGenerator(renderingCtx)
        generator.render()
        val contents = writer.toString()

        val expectedCompanionOverride = """
            companion object {
                fun foo(): Int = 1
            }
        """.formatForTest()
        contents.shouldContainOnlyOnce(expectedCompanionOverride)

        val expectedConfigOverride = """
            class Config {
                var bar: Int = 2
            }
        """.formatForTest()
        contents.shouldContainOnlyOnce(expectedConfigOverride)
    }

    // Produce the generated service code given model inputs.
    private fun generateService(modelResourceName: String): String {
        val model = javaClass
            .classLoader
            .getResource("software/amazon/smithy/kotlin/codegen/$modelResourceName")!!
            .toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val service = model.getShape(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID)).get().asServiceShape().get()
        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings(TestModelDefault.NAMESPACE, TestModelDefault.MODEL_VERSION), sdkId = service.id.name)
        val renderingCtx = RenderingContext(writer, service, model, provider, settings)
        val generator = ServiceGenerator(renderingCtx)

        generator.render()

        return writer.toString()
    }
}
