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
import software.amazon.smithy.kotlin.codegen.integration.*

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val writer: KotlinWriter = KotlinWriter("com.test")

    class MockHttpMiddleware1 : ProtocolMiddleware {
        override val name: String = "MockHttpFeature1"
        override fun renderConfigure(writer: KotlinWriter) {
            writer.write("configurationField1 = \"testing\"")
        }
    }

    init {
        val model = javaClass.getResource("service-generator-test-operations.smithy").asSmithy()
        val ctx = model.newTestContext("com.test#Example")
        val middlewares: List<ProtocolMiddleware> = listOf(MockHttpMiddleware1())
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            middlewares,
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
        commonTestContents.shouldContainOnlyOnce("import ${KotlinDependency.CLIENT_RT_SERDE_JSON.namespace}.JsonSerdeProvider")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnce("internal class DefaultExampleClient(private val config: ExampleClient.Config) : ExampleClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnce("val client: SdkHttpClient")
        val expected = """
    init {
        val httpClientEngine = config.httpClientEngine ?: KtorEngine(HttpClientEngineConfig())
        client = sdkHttpClient(httpClientEngine)
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
        val op = SdkHttpOperation.build<GetFooRequest, GetFooResponse> {
            serializer = GetFooOperationSerializer()
            deserializer = GetFooOperationDeserializer()
            context {
                expectedHttpStatus = 200
                service = serviceName
                operationName = "GetFoo"
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        registerDefaultMiddleware(op)
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
                set(SerdeAttributes.SerdeProvider, serde)
            }
        }
        """
        contents.shouldContainOnlyOnceWithDiff(expectedFragment)
    }
}
