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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.HttpFeature
import software.amazon.smithy.kotlin.codegen.integration.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.integration.HttpSerde
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val writer: KotlinWriter = KotlinWriter("com.test")

    class MockHttpFeature1 : HttpFeature {
        override val name: String = "MockHttpFeature1"
        override fun renderConfigure(writer: KotlinWriter) {
            writer.write("configurationField1 = \"testing\"")
        }
    }

    class MockHttpSerde : HttpSerde("MockSerdeProvider") {
        override fun addImportsAndDependencies(writer: KotlinWriter) {
            super.addImportsAndDependencies(writer)
            val serdeJsonSymbol = Symbol.builder()
                .name("JsonSerdeProvider")
                .namespace(KotlinDependency.CLIENT_RT_SERDE_JSON.namespace, ".")
                .addDependency(KotlinDependency.CLIENT_RT_SERDE_JSON)
                .build()
            writer.addImport(serdeJsonSymbol, "")
        }
    }

    init {
        val model = Model.assembler()
            .addImport(javaClass.getResource("service-generator-test-operations.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()

        val features: List<HttpFeature> = listOf(MockHttpFeature1(), MockHttpSerde())
        val generator = HttpProtocolClientGenerator(model, provider, writer, service, "test", features)
        generator.render()
        commonTestContents = writer.toString()
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import test.model.*")
        commonTestContents.shouldContainOnlyOnce("import test.transform.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine.HttpClientEngineConfig")

        // test for feature imports that are added
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP_FEAT_SERDE.namespace}.HttpSerde")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_SERDE_JSON.namespace}.JsonSerdeProvider")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnce("class DefaultExampleClient(config: ExampleClient.Config) : ExampleClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnce("val client: SdkHttpClient")
    val expected = """
    init {
        val engineConfig = HttpClientEngineConfig()
        val httpEngine = config.httpEngine ?: KtorEngine(engineConfig)
        client = sdkHttpClient(httpEngine) {
            install(MockHttpFeature1) {
                configurationField1 = "testing"
            }
            install(HttpSerde) {
                serdeProvider = MockSerdeProvider()
            }
        }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders close`() {
        val expected = """
    override fun close() {
        client.close()
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders operation bodies`() {
        val expectedBodies = listOf(
"""
    override suspend fun getFoo(input: GetFooRequest): GetFooResponse {
        return client.roundTrip(GetFooSerializer(input), GetFooDeserializer())
    }
""",
"""
    override suspend fun getFooNoInput(): GetFooResponse {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.GET
            url.path = "/foo-no-input"
        }
        return client.roundTrip(builder, GetFooNoInputDeserializer())
    }
""",
"""
    override suspend fun getFooNoOutput(input: GetFooRequest) {
        client.roundTrip<HttpResponse>(GetFooNoOutputSerializer(input), null)
    }
""",
"""
    override suspend fun getFooStreamingInput(input: GetFooStreamingRequest): GetFooResponse {
        return client.roundTrip(GetFooStreamingInputSerializer(input), GetFooStreamingInputDeserializer())
    }
""",
"""
    override suspend fun <T> getFooStreamingOutput(input: GetFooRequest, block: suspend (GetFooStreamingResponse) -> T): T {
        return client.execute(GetFooStreamingOutputSerializer(input), GetFooStreamingOutputDeserializer(), block)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutputNoInput(block: suspend (GetFooStreamingResponse) -> T): T {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url.path = "/foo-streaming-output-no-input"
        }
        return client.execute(builder, GetFooStreamingOutputNoInputDeserializer(), block)
    }
""",
"""
    override suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingRequest) {
        client.roundTrip<HttpResponse>(GetFooStreamingInputNoOutputSerializer(input), null)
    }
"""
        )
        expectedBodies.forEach {
            commonTestContents.shouldContainOnlyOnce(it)
        }
    }

    @Test
    fun `it registers feature dependencies`() {
        // Serde, HttpSerde, Http, KtorEngine, + SerdeJson (via feature)
        val ktDependencies = writer.dependencies.map { it.properties["dependency"] as KotlinDependency }.distinct()
        assertEquals(5, ktDependencies.size)
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        var openBraces = 0
        var closedBraces = 0
        var openParens = 0
        var closedParens = 0
        commonTestContents.forEach {
            when (it) {
                '{' -> openBraces++
                '}' -> closedBraces++
                '(' -> openParens++
                ')' -> closedParens++
            }
        }
        assertEquals(openBraces, closedBraces)
        assertEquals(openParens, closedParens)
    }
}
