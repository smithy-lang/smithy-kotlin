/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import kotlin.test.assertEquals
import kotlin.test.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.kotlin.codegen.trimEveryLine

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val deprecatedTestContents: String
    private val writer: KotlinWriter = KotlinWriter(TestModelDefault.NAMESPACE)

    class MockProtocolMiddleware1 : HttpFeatureMiddleware() {
        override val name: String = "MockProtocolMiddleware1"
        override fun renderConfigure(writer: KotlinWriter) {
            writer.write("configurationField1 = \"testing\"")
        }
    }

    init {
        commonTestContents = generateService("service-generator-test-operations.smithy")
        deprecatedTestContents = generateService("service-generator-deprecated.smithy")
    }

    @Test
    fun `it imports external symbols`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${TestModelDefault.NAMESPACE}.model.*")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${TestModelDefault.NAMESPACE}.transform.*")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.SdkHttpClient")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.sdkHttpClient")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.SdkHttpOperation")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.context")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.execute")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.roundTrip")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.engine.HttpClientEngineConfig")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("internal class DefaultTestClient(override val config: TestClient.Config) : TestClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("val client: SdkHttpClient")
        val expected = """
    init {
        val httpClientEngine = config.httpClientEngine ?: KtorEngine(HttpClientEngineConfig())
        client = sdkHttpClient(httpClientEngine, manageEngine = config.httpClientEngine == null)
    }
"""
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders close`() {
        val expected = """
    override fun close() {
        client.close()
    }
"""
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders operation bodies`() {
        val expectedBodies = listOf(
"""
    override suspend fun getFoo(input: GetFooRequest): GetFooResponse {
        val op = SdkHttpOperation.build<GetFooRequest, GetFooResponse> {
            serializer = GetFooOperationSerializer()
            deserializer = GetFooOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFoo"
            }
        }
        registerGetFooMiddleware(config, op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooNoInput(input: GetFooNoInputRequest): GetFooNoInputResponse {
        val op = SdkHttpOperation.build<GetFooNoInputRequest, GetFooNoInputResponse> {
            serializer = GetFooNoInputOperationSerializer()
            deserializer = GetFooNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooNoInput"
            }
        }
        registerGetFooNoInputMiddleware(config, op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooNoOutputRequest, GetFooNoOutputResponse> {
            serializer = GetFooNoOutputOperationSerializer()
            deserializer = GetFooNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooNoOutput"
            }
        }
        registerGetFooNoOutputMiddleware(config, op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputRequest, GetFooStreamingInputResponse> {
            serializer = GetFooStreamingInputOperationSerializer()
            deserializer = GetFooStreamingInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingInput"
            }
        }
        registerGetFooStreamingInputMiddleware(config, op)
        return op.roundTrip(client, input)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputRequest, GetFooStreamingOutputResponse> {
            serializer = GetFooStreamingOutputOperationSerializer()
            deserializer = GetFooStreamingOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingOutput"
            }
        }
        registerGetFooStreamingOutputMiddleware(config, op)
        return op.execute(client, input, block)
    }
""",
"""
    override suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputNoInputRequest, GetFooStreamingOutputNoInputResponse> {
            serializer = GetFooStreamingOutputNoInputOperationSerializer()
            deserializer = GetFooStreamingOutputNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingOutputNoInput"
            }
        }
        registerGetFooStreamingOutputNoInputMiddleware(config, op)
        return op.execute(client, input, block)
    }
""",
"""
    override suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputNoOutputRequest, GetFooStreamingInputNoOutputResponse> {
            serializer = GetFooStreamingInputNoOutputOperationSerializer()
            deserializer = GetFooStreamingInputNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFooStreamingInputNoOutput"
            }
        }
        registerGetFooStreamingInputNoOutputMiddleware(config, op)
        return op.roundTrip(client, input)
    }
"""
        )
        expectedBodies.forEach {
            commonTestContents.shouldContainOnlyOnceWithDiff(it)
        }
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
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.AwsJson1_1, operations = listOf("GetStatus"))
            .toSmithyModel()

        val ctx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            listOf(),
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        val contents = writer.toString()

        val prefix = "\${input.foo}.data."
        val expectedFragment = """
        val op = SdkHttpOperation.build<GetStatusRequest, GetStatusResponse> {
            serializer = GetStatusOperationSerializer()
            deserializer = GetStatusOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetStatus"
                hostPrefix = "$prefix"
            }
        }
        """
        contents.shouldContainOnlyOnceWithDiff(expectedFragment)
    }

    @Test
    fun `it annotates deprecated operation functions`() {
        deprecatedTestContents.trimEveryLine().shouldContainOnlyOnceWithDiff(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                override suspend fun yeOldeOperation(input: YeOldeOperationRequest): YeOldeOperationResponse {
            """.trimIndent()
        )
    }

    private fun generateService(modelResourceName: String): String {
        val model = loadModelFromResource(modelResourceName)

        val ctx = model.newTestContext()
        val features: List<ProtocolMiddleware> = listOf(MockProtocolMiddleware1())
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            features,
            HttpTraitResolver(ctx.generationCtx, "application/json")
        )
        generator.render(writer)
        return writer.toString()
    }
}
