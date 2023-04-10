/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.kotlin.codegen.trimEveryLine
import software.amazon.smithy.model.shapes.OperationShape
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpProtocolClientGeneratorTest {
    private val commonTestContents: String
    private val deprecatedTestContents: String
    private val writer: KotlinWriter = KotlinWriter(TestModelDefault.NAMESPACE)

    class MockProtocolMiddleware1 : ProtocolMiddleware {
        override val name: String = "MockProtocolMiddleware1"

        override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
            writer.write("op.install(MockMiddleware(configurationField1 = \"testing\"))")
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
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.SdkHttpOperation")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.context")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.execute")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.roundTrip")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.HTTP.namespace}.operation.sdkRequestId")
        commonTestContents.shouldContainOnlyOnceWithDiff("import ${KotlinDependency.TRACING_CORE.namespace}.withRootTraceSpan")
    }

    @Test
    fun `it renders constructor`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("internal class DefaultTestClient(override val config: TestClient.Config) : TestClient {")
    }

    @Test
    fun `it renders properties and init`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("val managedResources = SdkManagedGroup()")
        commonTestContents.shouldContainOnlyOnceWithDiff("val client = SdkHttpClient(config.httpClientEngine)")
        val expected = """
    init {
        managedResources.addIfManaged(config.httpClientEngine)
    }
"""
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders close`() {
        val expected = """
    override fun close() {
        managedResources.unshareAll()
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
                operationName = "GetFoo"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFoo-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun getFooNoInput(input: GetFooNoInputRequest): GetFooNoInputResponse {
        val op = SdkHttpOperation.build<GetFooNoInputRequest, GetFooNoInputResponse> {
            serializer = GetFooNoInputOperationSerializer()
            deserializer = GetFooNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooNoInput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooNoInput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun getFooNoOutput(input: GetFooNoOutputRequest): GetFooNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooNoOutputRequest, GetFooNoOutputResponse> {
            serializer = GetFooNoOutputOperationSerializer()
            deserializer = GetFooNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooNoOutput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooNoOutput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun getFooStreamingInput(input: GetFooStreamingInputRequest): GetFooStreamingInputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputRequest, GetFooStreamingInputResponse> {
            serializer = GetFooStreamingInputOperationSerializer()
            deserializer = GetFooStreamingInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooStreamingInput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooStreamingInput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun <T> getFooStreamingOutput(input: GetFooStreamingOutputRequest, block: suspend (GetFooStreamingOutputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputRequest, GetFooStreamingOutputResponse> {
            serializer = GetFooStreamingOutputOperationSerializer()
            deserializer = GetFooStreamingOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooStreamingOutput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooStreamingOutput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.execute(client, input, block)
        }
    }
""",
"""
    override suspend fun <T> getFooStreamingOutputNoInput(input: GetFooStreamingOutputNoInputRequest, block: suspend (GetFooStreamingOutputNoInputResponse) -> T): T {
        val op = SdkHttpOperation.build<GetFooStreamingOutputNoInputRequest, GetFooStreamingOutputNoInputResponse> {
            serializer = GetFooStreamingOutputNoInputOperationSerializer()
            deserializer = GetFooStreamingOutputNoInputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooStreamingOutputNoInput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooStreamingOutputNoInput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.execute(client, input, block)
        }
    }
""",
"""
    override suspend fun getFooStreamingInputNoOutput(input: GetFooStreamingInputNoOutputRequest): GetFooStreamingInputNoOutputResponse {
        val op = SdkHttpOperation.build<GetFooStreamingInputNoOutputRequest, GetFooStreamingInputNoOutputResponse> {
            serializer = GetFooStreamingInputNoOutputOperationSerializer()
            deserializer = GetFooStreamingInputNoOutputOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooStreamingInputNoOutput"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooStreamingInputNoOutput-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun getFooNoRequired(input: GetFooNoRequiredRequest): GetFooNoRequiredResponse {
        val op = SdkHttpOperation.build<GetFooNoRequiredRequest, GetFooNoRequiredResponse> {
            serializer = GetFooNoRequiredOperationSerializer()
            deserializer = GetFooNoRequiredOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooNoRequired"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooNoRequired-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
"""
    override suspend fun getFooSomeRequired(input: GetFooSomeRequiredRequest): GetFooSomeRequiredResponse {
        val op = SdkHttpOperation.build<GetFooSomeRequiredRequest, GetFooSomeRequiredResponse> {
            serializer = GetFooSomeRequiredOperationSerializer()
            deserializer = GetFooSomeRequiredOperationDeserializer()
            context {
                expectedHttpStatus = 200
                operationName = "GetFooSomeRequired"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
        }
        op.install(MockMiddleware(configurationField1 = "testing"))
        op.interceptors.addAll(config.interceptors)
        val rootSpan = config.tracer.createRootSpan("GetFooSomeRequired-${'$'}{op.context.sdkRequestId}")
        return coroutineContext.withRootTraceSpan(rootSpan) {
            op.roundTrip(client, input)
        }
    }
""",
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
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.AWS_JSON_1_1, operations = listOf("GetStatus"))
            .toSmithyModel()

        val ctx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            listOf(),
            HttpTraitResolver(ctx.generationCtx, "application/json"),
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
                operationName = "GetStatus"
                hostPrefix = "$prefix"
            }
            execution.auth = OperationAuthConfig(AuthSchemeProviderAdapter, configuredAuthSchemes, identityProviderConfig)
            execution.retryStrategy = config.retryStrategy
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
            """.trimIndent(),
        )
    }

    private fun generateService(modelResourceName: String): String {
        val model = loadModelFromResource(modelResourceName)

        val ctx = model.newTestContext()
        val features: List<ProtocolMiddleware> = listOf(MockProtocolMiddleware1())
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            features,
            HttpTraitResolver(ctx.generationCtx, "application/json"),
        )
        generator.render(writer)
        return writer.toString()
    }
}
