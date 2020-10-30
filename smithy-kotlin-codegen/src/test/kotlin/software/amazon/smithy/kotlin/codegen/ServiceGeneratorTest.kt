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
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

class ServiceGeneratorTest {
    private val commonTestContents: String

    init {
        val model = Model.assembler()
            .addImport(javaClass.getResource("service-generator-test-operations.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val applicationProtocol = ApplicationProtocol.createDefaultHttpApplicationProtocol()
        val generator = ServiceGenerator(model, provider, writer, service, "test", applicationProtocol)
        generator.render()

        commonTestContents = writer.toString()
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import test.model.*")
        commonTestContents.shouldContainOnlyOnce("import $CLIENT_RT_ROOT_NS.SdkClient")
    }

    @Test
    fun `it renders interface`() {
        commonTestContents.shouldContainOnlyOnce("interface ExampleClient : SdkClient {")
    }

    @Test
    fun `it overrides SdkClient serviceName`() {
        val expected = """
    override val serviceName: String
        get() = "Example"
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders signatures correctly`() {
        val expectedSignatures = listOf(
            "suspend fun getFoo(input: GetFooRequest): GetFooResponse",
            "suspend fun getFooNoInput(): GetFooResponse",
            "suspend fun getFooNoOutput(input: GetFooRequest)",
            "suspend fun getFooStreamingInput(input: GetFooStreamingRequest): GetFooResponse",
            "suspend fun <T> getFooStreamingOutput(input: GetFooRequest, block: suspend (GetFooStreamingResponse) -> T): T",
            "suspend fun <T> getFooStreamingOutputNoInput(block: suspend (GetFooStreamingResponse) -> T): T",
            "suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingRequest)"
        )
        expectedSignatures.forEach {
            commonTestContents.shouldContainOnlyOnce(it)
        }
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
    companion object {
        operator fun invoke(block: Config.DslBuilder.() -> Unit = {}): ExampleClient {
            val config = Config.BuilderImpl().apply(block).build()
            return DefaultExampleClient(config)
        }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it generates a child configuration type`() {
        // we are using a default HTTP protocol in the test and so we should end up with an engine by default
        val expected = """
    class Config private constructor(builder: BuilderImpl): HttpClientConfig, IdempotencyTokenConfig {

        override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
        override val httpClientEngineConfig: HttpClientEngineConfig? = builder.httpClientEngineConfig
        override val idempotencyTokenProvider: IdempotencyTokenProvider? = builder.idempotencyTokenProvider

        companion object {
            @JvmStatic
            fun builder(): Builder = BuilderImpl()
            fun dslBuilder(): DslBuilder = BuilderImpl()
            operator fun invoke(block: DslBuilder.() -> Unit): Config = BuilderImpl().apply(block).build()
        }

        fun copy(block: DslBuilder.() -> Unit = {}): Config = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): Config
            fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder
            fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder
            fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder
        }

        interface DslBuilder {
            fun build(): Config
            var httpClientEngine: HttpClientEngine?
            var httpClientEngineConfig: HttpClientEngineConfig?
            var idempotencyTokenProvider: IdempotencyTokenProvider?
        }

        internal class BuilderImpl() : Builder, DslBuilder {
            override var httpEngine: HttpClientEngine? = null
            override var httpClientEngineConfig: HttpClientEngineConfig? = null
            override var idempotencyTokenProvider: IdempotencyTokenProvider? = null

            constructor(config: Config) : this() {
                this.httpEngine = config.httpEngine
                this.httpClientEngineConfig = config.httpClientEngineConfig
                this.idempotencyTokenProvider = config.idempotencyTokenProvider
            }

            override fun httpEngine(httpEngine: HttpClientEngine): Builder = apply { this.httpEngine = httpEngine }
            override fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder = apply { this.httpClientEngineConfig = httpClientEngineConfig }
            override fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder = apply { this.idempotencyTokenProvider = idempotencyTokenProvider }
        }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        commonTestContents.shouldSyntacticSanityCheck()
    }

    @Test
    fun `it allows overriding defined sections`() {
        val model = Model.assembler()
            .addImport(javaClass.getResource("service-generator-test-operations.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val applicationProtocol = ApplicationProtocol.createDefaultHttpApplicationProtocol()
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

        val generator = ServiceGenerator(model, provider, writer, service, "test", applicationProtocol)
        generator.render()
        val contents = writer.toString()

        val expectedCompanionOverride = """
    companion object {
        fun foo(): Int = 1
    }
"""
        contents.shouldContainOnlyOnce(expectedCompanionOverride)

        val expectedConfigOverride = """
    class Config {
        var bar: Int = 2
    }
"""
        contents.shouldContainOnlyOnce(expectedConfigOverride)
    }
}
