/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.matchers.string.shouldNotContain
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

class ServiceClientGeneratorTest {
    private val commonTestContents: String
    private val commonWithProtocolTestContents: String
    private val deprecatedTestContents: String

    init {
        commonTestContents = generateService("service-generator-test-operations.smithy")
        commonWithProtocolTestContents = generateService("service-generator-test-operations.smithy", withProtocolGenerator = true)
        deprecatedTestContents = generateService("service-generator-deprecated.smithy")
    }

    @Test
    fun `it renders interface`() {
        commonTestContents.shouldContainOnlyOnce("public interface TestClient : SdkClient {")
    }

    @Test
    fun `it renders signatures correctly`() {
        val expectedSignatures = listOf(
            "public suspend fun getFoo(input: GetFooRequest): GetFooResponse",
            "public suspend fun getFooNoRequired(input: GetFooNoRequiredRequest = GetFooNoRequiredRequest {}): GetFooNoRequiredResponse",
            "public suspend fun getFooSomeRequired(input: GetFooSomeRequiredRequest): GetFooSomeRequiredResponse",
            "public suspend fun getFooNoInput(input: GetFooNoInputRequest = GetFooNoInputRequest {}): GetFooNoInputResponse",
            "public suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse",
            "public suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse",
            "public suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T",
            "public suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest = GetFooStreamingOutputNoInputRequest {}, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T",
            "public suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse",
        )
        expectedSignatures.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
    }

    @Test
    fun `it renders a companion object with default client factory if protocol generator`() {
        val expected = """
            public companion object : SdkClientFactory<Config, Config.Builder, TestClient, Builder> {
                @JvmStatic
                override fun builder(): Builder = Builder()
            }
        """.formatForTest()
        commonWithProtocolTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a companion object without default client factory if no protocol generator`() {
        val expected = """
            public companion object : SdkClientFactory
        """.formatForTest()
        commonTestContents.shouldNotContain(expected)
    }

    @Test
    fun `it generates config`() {
        val expected = "public class Config private constructor(builder: Builder) : IdempotencyTokenConfig, SdkClientConfig, TelemetryConfig"
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
        writer.registerSectionWriter(ServiceClientGenerator.Sections.CompanionObject) { codeWriter, _ ->
            codeWriter.openBlock("public companion object {")
                .write("public fun foo(): Int = 1")
                .closeBlock("}")
        }

        writer.registerSectionWriter(ServiceClientGenerator.Sections.ServiceConfig) { codeWriter, _ ->
            codeWriter.openBlock("public class Config {")
                .write("public var bar: Int = 2")
                .closeBlock("}")
        }

        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings("test", "0.0"), sdkId = service.id.name)
        val renderingCtx = RenderingContext(writer, service, model, provider, settings)
        val generator = ServiceClientGenerator(renderingCtx)
        generator.render()
        val contents = writer.toString()

        val expectedCompanionOverride = """
            public companion object {
                public fun foo(): Int = 1
            }
        """.formatForTest()
        contents.shouldContainOnlyOnce(expectedCompanionOverride)

        val expectedConfigOverride = """
            public class Config {
                public var bar: Int = 2
            }
        """.formatForTest()
        contents.shouldContainOnlyOnce(expectedConfigOverride)
    }

    @Test
    fun `it annotates deprecated service interfaces`() {
        deprecatedTestContents.shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public interface TestClient : SdkClient {
            """.trimIndent(),
        )
    }

    @Test
    fun `it annotates deprecated operation functions`() {
        deprecatedTestContents.trimEveryLine().shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public suspend fun yeOldeOperation(input: YeOldeOperationRequest): YeOldeOperationResponse
            """.trimIndent(),
        )
    }

    @Test
    fun `it adds DSL overloads for operations`() {
        listOf(
            "GetFoo",
            "GetFooNoInput",
            "GetFooNoOutput",
            "GetFooStreamingInput",
            "GetFooStreamingInputNoOutput",
        ).forEach { op ->
            val modifiers = "public suspend inline fun"
            val method = op.replaceFirstChar(Char::lowercaseChar)
            val params = "(crossinline block: ${op}Request.Builder.() -> Unit)"
            val impl = "$method(${op}Request.Builder().apply(block).build())"

            val expected = "$modifiers TestClient.$method$params: ${op}Response = $impl"
            commonTestContents.shouldContainOnlyOnceWithDiff(expected)
        }
    }

    @Test
    fun `it renders a service client builder`() {
        val expected = """
            public class Builder internal constructor(): AbstractSdkClientBuilder<Config, Config.Builder, TestClient>() {
                override val config: Config.Builder = Config.Builder()
                override fun newClient(config: Config): TestClient = DefaultTestClient(config)
            }
        """.formatForTest()
        val testContents = generateService("service-generator-test-operations.smithy", true)
        testContents.shouldContainOnlyOnceWithDiff(expected)
    }

    fun `it renders withConfig extension`() {
        val expected = """
            public fun TestClient.withConfig(block: TestClient.Config.Builder.() -> Unit): TestClient {
                val newConfig = config.toBuilder().apply(block).build()
                return DefaultTestClient(newConfig)
            }
        """.trimIndent()
        commonWithProtocolTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it skips withConfig extension if no protocolGenerator`() {
        val expected = """
            public fun TestClient.withConfig(block: TestClient.Config.Builder.() -> Unit): TestClient {
                val newConfig = config.toBuilder().apply(block).build()
                return DefaultTestClient(newConfig)
            }
        """.trimIndent()
        commonTestContents.shouldNotContainOnlyOnceWithDiff(expected)
    }

    // Produce the generated service code given model inputs.
    private fun generateService(modelResourceName: String, withProtocolGenerator: Boolean = false): String {
        val model = loadModelFromResource(modelResourceName)

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val service = model.getShape(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID)).get().asServiceShape().get()
        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings(TestModelDefault.NAMESPACE, TestModelDefault.MODEL_VERSION), sdkId = service.id.name)
        val protocolGenerator = if (withProtocolGenerator) MockHttpProtocolGenerator(model) else null
        val renderingCtx = RenderingContext(writer, service, model, provider, settings, protocolGenerator)
        val generator = ServiceClientGenerator(renderingCtx)

        generator.render()

        return writer.toString()
    }
}
