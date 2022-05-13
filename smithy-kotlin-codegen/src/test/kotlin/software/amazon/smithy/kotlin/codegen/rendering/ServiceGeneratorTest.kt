/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.kotlin.codegen.trimEveryLine
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

class ServiceGeneratorTest {
    private val commonTestContents: String
    private val deprecatedTestContents: String

    init {
        commonTestContents = generateService("service-generator-test-operations.smithy")
        deprecatedTestContents = generateService("service-generator-deprecated.smithy")
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import ${TestModelDefault.NAMESPACE}.model.*")
        commonTestContents.shouldContainOnlyOnce("import $RUNTIME_ROOT_NS.SdkClient")
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
            "suspend fun getFooNoRequired(input: GetFooNoRequiredRequest = GetFooNoRequiredRequest{}): GetFooNoRequiredResponse",
            "suspend fun getFooSomeRequired(input: GetFooSomeRequiredRequest): GetFooSomeRequiredResponse",
            "suspend fun getFooNoInput(input: GetFooNoInputRequest = GetFooNoInputRequest{}): GetFooNoInputResponse",
            "suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse",
            "suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse",
            "suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T",
            "suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest = GetFooStreamingOutputNoInputRequest{}, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T",
            "suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse"
        )
        expectedSignatures.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
    }

    @Test
    fun `it renders a companion object with default client factory if protocol generator`() {
        val expected = """
            companion object {
                operator fun invoke(block: Config.Builder.() -> Unit = {}): TestClient {
                    val config = Config.Builder().apply(block).build()
                    return DefaultTestClient(config)
                }

                operator fun invoke(config: Config): TestClient = DefaultTestClient(config)
            }
        """.formatForTest()
        val testContents = generateService("service-generator-test-operations.smithy", true)
        testContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a companion object without default client factory if no protocol generator`() {
        val expected = """
            companion object {
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
        val model = loadModelFromResource("service-generator-test-operations.smithy")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val service = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)
        writer.registerSectionWriter(ServiceGenerator.SectionServiceCompanionObject) { codeWriter, _ ->
            codeWriter.openBlock("companion object {")
                .write("fun foo(): Int = 1")
                .closeBlock("}")
        }

        writer.registerSectionWriter(ServiceGenerator.SectionServiceConfig) { codeWriter, _ ->
            codeWriter.openBlock("class Config {")
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

    @Test
    fun `it annotates deprecated service interfaces`() {
        deprecatedTestContents.shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                interface TestClient : SdkClient {
            """.trimIndent()
        )
    }

    @Test
    fun `it annotates deprecated operation functions`() {
        deprecatedTestContents.trimEveryLine().shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                suspend fun yeOldeOperation(input: YeOldeOperationRequest): YeOldeOperationResponse
            """.trimIndent()
        )
    }

    @Test
    fun `it adds DSL overloads for operations`() {
        val expectedSignatures = listOf(
            "suspend fun getFoo(block: GetFooRequest.Builder.() -> Unit) = getFoo(GetFooRequest.Builder().apply(block).build())",
            "suspend fun getFooNoInput(block: GetFooNoInputRequest.Builder.() -> Unit) = getFooNoInput(GetFooNoInputRequest.Builder().apply(block).build())",
            "suspend fun getFooNoOutput(block: GetFooNoOutputRequest.Builder.() -> Unit) = getFooNoOutput(GetFooNoOutputRequest.Builder().apply(block).build())",
            "suspend fun getFooStreamingInput(block: GetFooStreamingInputRequest.Builder.() -> Unit) = getFooStreamingInput(GetFooStreamingInputRequest.Builder().apply(block).build())",
            "suspend fun getFooStreamingInputNoOutput(block: GetFooStreamingInputNoOutputRequest.Builder.() -> Unit) = getFooStreamingInputNoOutput(GetFooStreamingInputNoOutputRequest.Builder().apply(block).build())",
        )
        expectedSignatures.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
    }

    // Produce the generated service code given model inputs.
    private fun generateService(modelResourceName: String, withProtocolGenerator: Boolean = false): String {
        val model = loadModelFromResource(modelResourceName)

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val service = model.getShape(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID)).get().asServiceShape().get()
        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings(TestModelDefault.NAMESPACE, TestModelDefault.MODEL_VERSION), sdkId = service.id.name)
        val protocolGenerator = if (withProtocolGenerator) MockHttpProtocolGenerator() else null
        val renderingCtx = RenderingContext(writer, service, model, provider, settings, protocolGenerator)
        val generator = ServiceGenerator(renderingCtx)

        generator.render()

        return writer.toString()
    }
}
