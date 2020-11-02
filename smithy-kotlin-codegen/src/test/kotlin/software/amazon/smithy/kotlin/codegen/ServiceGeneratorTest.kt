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
import jdk.nashorn.internal.ir.annotations.Ignore
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

class ServiceGeneratorTest {
    private val commonTestContents: String

    init {
        commonTestContents = generateService("service-generator-test-operations.smithy") {
            ApplicationProtocol.createDefaultHttpApplicationProtocol()
        }
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
    fun `it generates a child configuration type with HttpClientConfig and IdempotencyTokenConfig based on model`() {
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

            override var httpClientEngine: HttpClientEngine? = null
            override var httpClientEngineConfig: HttpClientEngineConfig? = null
            override var idempotencyTokenProvider: IdempotencyTokenProvider? = null

            constructor(config: Config) : this() {

                this.httpClientEngine = config.httpClientEngine
                this.httpClientEngineConfig = config.httpClientEngineConfig
                this.idempotencyTokenProvider = config.idempotencyTokenProvider
            }

            override fun build(): Config = Config(this)

            override fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder = apply { this.httpClientEngine = httpClientEngine }
            override fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder = apply { this.httpClientEngineConfig = httpClientEngineConfig }
            override fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder = apply { this.idempotencyTokenProvider = idempotencyTokenProvider }
        }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    // @Test
    // It's unclear if we need to write codegen for the situation in which a service client has no configuration.
    fun `it generates a child configuration type`() {
        val expected = """
    class Config private constructor(builder: BuilderImpl) {

        companion object {
            @JvmStatic
            fun builder(): Builder = BuilderImpl()
            fun dslBuilder(): DslBuilder = BuilderImpl()
            operator fun invoke(block: DslBuilder.() -> Unit): Config = BuilderImpl().apply(block).build()
        }

        fun copy(block: DslBuilder.() -> Unit = {}): Config = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): Config
        }

        interface DslBuilder {
            fun build(): Config
        }

        internal class BuilderImpl() : Builder, DslBuilder {

            constructor(config: Config) : this() {
            }

            override fun build(): Config = Config(this)
        }
    }
"""
        generateService("service-generator-test-minimal-operations.smithy") {
            ApplicationProtocol(
                    "nothttp",
                    createHttpSymbol("NotHttpRequestBuilder", "request"),
                    createHttpSymbol("NotHttpResponse", "response")
            )
        }.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it generates a child configuration type for Http application protocol`() {
        val expected = """
    class Config private constructor(builder: BuilderImpl): HttpClientConfig {

        override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
        override val httpClientEngineConfig: HttpClientEngineConfig? = builder.httpClientEngineConfig

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
        }

        interface DslBuilder {
            fun build(): Config

            var httpClientEngine: HttpClientEngine?
            var httpClientEngineConfig: HttpClientEngineConfig?
        }

        internal class BuilderImpl() : Builder, DslBuilder {

            override var httpClientEngine: HttpClientEngine? = null
            override var httpClientEngineConfig: HttpClientEngineConfig? = null

            constructor(config: Config) : this() {

                this.httpClientEngine = config.httpClientEngine
                this.httpClientEngineConfig = config.httpClientEngineConfig
            }

            override fun build(): Config = Config(this)

            override fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder = apply { this.httpClientEngine = httpClientEngine }
            override fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder = apply { this.httpClientEngineConfig = httpClientEngineConfig }
        }
    }
"""
        generateService("service-generator-test-minimal-operations.smithy") {
            ApplicationProtocol.createDefaultHttpApplicationProtocol()
        }.shouldContainOnlyOnce(expected)
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

    // Produce the generated service code given model inputs.
    private fun generateService(modelResourceName: String, applicationProtocolFactory: () -> ApplicationProtocol): String {
        val model = Model.assembler()
                .addImport(javaClass.getResource(modelResourceName))
                .discoverModels()
                .assemble()
                .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val generator = ServiceGenerator(model, provider, writer, service, "test", applicationProtocolFactory())
        generator.render()

        return writer.toString()
    }

    private fun createHttpSymbol(symbolName: String, subnamespace: String): Symbol {
        return Symbol.builder()
                .name(symbolName)
                .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.$subnamespace", ".")
                .addDependency(KotlinDependency.CLIENT_RT_HTTP)
                .build()
    }
}
