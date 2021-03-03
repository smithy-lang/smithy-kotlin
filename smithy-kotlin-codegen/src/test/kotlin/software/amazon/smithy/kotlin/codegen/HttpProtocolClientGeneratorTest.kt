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
import software.amazon.smithy.kotlin.codegen.integration.HttpFeature
import software.amazon.smithy.kotlin.codegen.integration.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.integration.HttpSerde
import software.amazon.smithy.kotlin.codegen.integration.HttpTraitResolver

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val writer: KotlinWriter = KotlinWriter("com.test")

    class MockHttpFeature1 : HttpFeature {
        override val name: String = "MockHttpFeature1"
        override fun renderConfigure(writer: KotlinWriter) {
            writer.write("configurationField1 = \"testing\"")
        }
    }

    class MockHttpSerde : HttpSerde("MockSerdeProvider", true) {
        override fun addImportsAndDependencies(writer: KotlinWriter) {
            super.addImportsAndDependencies(writer)
            val serdeJsonSymbol = Symbol.builder()
                .name("JsonSerdeProvider")
                .namespace(KotlinDependency.CLIENT_RT_SERDE_JSON.namespace, ".")
                .addDependency(KotlinDependency.CLIENT_RT_SERDE_JSON)
                .build()
            writer.addImport(serdeJsonSymbol)
        }
    }

    init {
        val model = javaClass.getResource("service-generator-test-operations.smithy").asSmithy()
        val ctx = model.newTestContext("com.test#Example")
        val features: List<HttpFeature> = listOf(MockHttpFeature1(), MockHttpSerde())
        val generator = HttpProtocolClientGenerator(
            ctx.generationCtx, features,
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        commonTestContents = writer.toString()
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnce("import test.model.*")
        commonTestContents.shouldContainOnlyOnce("import test.transform.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.*")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine.HttpClientEngineConfig")

        // test for feature imports that are added
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature.HttpSerde")
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_SERDE_JSON.namespace}.JsonSerdeProvider")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnce("class DefaultExampleClient(private val config: ExampleClient.Config) : ExampleClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnce("val client: SdkHttpClient")
        val expected = """
    init {
        val engineConfig = HttpClientEngineConfig()
        val httpClientEngine = config.httpClientEngine ?: KtorEngine(engineConfig)
        client = sdkHttpClient(httpClientEngine) {
            install(MockHttpFeature1) {
                configurationField1 = "testing"
            }
            install(HttpSerde) {
                serdeProvider = MockSerdeProvider()
                idempotencyTokenProvider = config.idempotencyTokenProvider ?: IdempotencyTokenProvider.Default
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
        val execCtx = SdkHttpOperation.build {
            serializer = GetFooOperationSerializer(input)
            deserializer = GetFooOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFoo"
        }
        return client.roundTrip(execCtx, null)
    }
""",
"""
    override suspend fun getFooNoInput(): GetFooResponse {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.GET
            url.path = "/foo-no-input"
        }
        val execCtx = SdkHttpOperation.build {
            deserializer = GetFooNoInputOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooNoInput"
        }
        return client.roundTrip(execCtx, builder)
    }
""",
"""
    override suspend fun getFooNoOutput(input: GetFooRequest) {
        val execCtx = SdkHttpOperation.build {
            serializer = GetFooNoOutputOperationSerializer(input)
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooNoOutput"
        }
        client.roundTrip<HttpResponse>(execCtx, null)
    }
""",
"""
    override suspend fun getFooStreamingInput(input: GetFooStreamingRequest): GetFooResponse {
        val execCtx = SdkHttpOperation.build {
            serializer = GetFooStreamingInputOperationSerializer(input)
            deserializer = GetFooStreamingInputOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooStreamingInput"
        }
        return client.roundTrip(execCtx, null)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutput(input: GetFooRequest, block: suspend (GetFooStreamingResponse) -> T): T {
        val execCtx = SdkHttpOperation.build {
            serializer = GetFooStreamingOutputOperationSerializer(input)
            deserializer = GetFooStreamingOutputOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooStreamingOutput"
        }
        return client.execute(execCtx, null, block)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutputNoInput(block: suspend (GetFooStreamingResponse) -> T): T {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url.path = "/foo-streaming-output-no-input"
        }
        val execCtx = SdkHttpOperation.build {
            deserializer = GetFooStreamingOutputNoInputOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooStreamingOutputNoInput"
        }
        return client.execute(execCtx, builder, block)
    }
""",
"""
    override suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingRequest) {
        val execCtx = SdkHttpOperation.build {
            serializer = GetFooStreamingInputNoOutputOperationSerializer(input)
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetFooStreamingInputNoOutput"
        }
        client.roundTrip<HttpResponse>(execCtx, null)
    }
"""
        )
        expectedBodies.forEach {
            commonTestContents.shouldContainOnlyOnce(it)
        }
    }

    @Test
    fun `it registers feature dependencies`() {
        // Serde, Http, KtorEngine, + SerdeJson (via feature)
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

    @Test
    fun `it handles endpointTrait hostPrefix with label`() {
        val model = """
            namespace com.test
            use aws.protocols#awsJson1_1

            @awsJson1_1
            service Example {
                version: "1.0.0",
                operations: [ GetStatus ]
            }

            @readonly
            @endpoint(hostPrefix: "{foo}.data.")
            @http(method: "POST", uri: "/status")
            operation GetStatus {
                input: GetStatusInput,
                output: GetStatusOutput
            }

            structure GetStatusInput {
                @required
                @hostLabel
                foo: String
            }
            
            structure GetStatusOutput {}
        """.asSmithyModel()

        val ctx = model.newTestContext("com.test#Example")
        val writer = KotlinWriter("com.test")
        val generator = HttpProtocolClientGenerator(
            ctx.generationCtx, listOf(),
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        val contents = writer.toString()

        val prefix = "\${input.foo}.data."
        val expectedFragment = """
        val execCtx = SdkHttpOperation.build {
            serializer = GetStatusOperationSerializer(input)
            deserializer = GetStatusOperationDeserializer()
            expectedHttpStatus = 200
            service = serviceName
            operationName = "GetStatus"
            hostPrefix = "$prefix"
        }
        """
        contents.shouldContainOnlyOnceWithDiff(expectedFragment)
    }
}
