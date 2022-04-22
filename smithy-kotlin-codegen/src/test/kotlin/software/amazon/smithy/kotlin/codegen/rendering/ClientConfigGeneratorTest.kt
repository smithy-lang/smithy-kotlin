/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import kotlin.test.Test

class ClientConfigGeneratorTest {
    private fun getModel(): Model = loadModelFromResource("idempotent-token-test-model.smithy")

    @Test
    fun `it detects default properties`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        ClientConfigGenerator(renderingCtx).render()
        val contents = writer.toString()

        contents.assertBalancedBracesAndParens()

        val expectedCtor = """
class Config private constructor(builder: Builder): HttpClientConfig, IdempotencyTokenConfig, SdkClientConfig {
"""
        contents.shouldContainWithDiff(expectedCtor)

        val expectedProps = """
    val endpointResolver: EndpointResolver = requireNotNull(builder.endpointResolver) { "endpointResolver is a required configuration property" }
    override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
    override val idempotencyTokenProvider: IdempotencyTokenProvider? = builder.idempotencyTokenProvider
    val retryStrategy: RetryStrategy = StandardRetryStrategy()
    override val sdkLogMode: SdkLogMode = builder.sdkLogMode
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedBuilder = """
    class Builder {
        /**
         * Set the [aws.smithy.kotlin.runtime.http.endpoints.EndpointResolver] used to resolve service endpoints. Operation requests will be
         * made against the endpoint returned by the resolver.
         */
        var endpointResolver: EndpointResolver? = null
        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc).
         * NOTE: The caller is responsible for managing the lifetime of the engine when set. The SDK
         * client will not close it when the client is closed.
         */
        var httpClientEngine: HttpClientEngine? = null
        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        var idempotencyTokenProvider: IdempotencyTokenProvider? = null
        /**
         * Configure events that will be logged. By default clients will not output
         * raw requests or responses. Use this setting to opt-in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        var sdkLogMode: SdkLogMode = SdkLogMode.Default

        @PublishedApi
        internal fun build(): Config = Config(this)
    }
"""
        contents.shouldContainWithDiff(expectedBuilder)

        val expectedImports = listOf(
            "import ${RuntimeTypes.Http.Endpoints.EndpointResolver.fullName}",
            "import ${RuntimeTypes.Http.Engine.HttpClientEngine.fullName}",
            "import ${KotlinDependency.HTTP.namespace}.config.HttpClientConfig",
            "import ${KotlinDependency.CORE.namespace}.config.IdempotencyTokenConfig",
            "import ${KotlinDependency.CORE.namespace}.config.IdempotencyTokenProvider",
            "import ${KotlinDependency.CORE.namespace}.config.SdkClientConfig",
            "import ${KotlinDependency.CORE.namespace}.client.SdkLogMode",
        )
        expectedImports.forEach {
            contents.shouldContain(it)
        }
    }

    @Test
    fun `it handles additional props`() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = arrayOf(
            ClientConfigProperty.Int("intProp", 1, documentation = "non-null-int"),
            ClientConfigProperty.Int("nullIntProp"),
            ClientConfigProperty.String("stringProp"),
            ClientConfigProperty.Boolean("boolProp"),
        )

        ClientConfigGenerator(renderingCtx, detectDefaultProps = false, builderReturnType = null, *customProps).render()
        val contents = writer.toString()

        // we should have no base classes when not using the default and no inheritFrom specified
        val expectedCtor = """
class Config private constructor(builder: Builder) {
"""
        contents.shouldContain(expectedCtor)

        val expectedProps = """
        var boolProp: Boolean? = null
        /**
         * non-null-int
         */
        var intProp: Int = 1
        var nullIntProp: Int? = null
        var stringProp: String? = null
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedBuilderProps = """
        var boolProp: Boolean? = null
        /**
         * non-null-int
         */
        var intProp: Int = 1
        var nullIntProp: Int? = null
        var stringProp: String? = null
"""
        contents.shouldContainWithDiff(expectedBuilderProps)
    }

    @Test
    fun `it registers integration props`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val customIntegration = object : KotlinIntegration {

            override fun additionalServiceConfigProps(ctx: CodegenContext): List<ClientConfigProperty> =
                listOf(ClientConfigProperty.Int("customProp"))
        }

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(customIntegration))

        ClientConfigGenerator(renderingCtx, detectDefaultProps = false).render()
        val contents = writer.toString()

        val expectedProps = """
    val customProp: Int? = builder.customProp
"""
        contents.shouldContain(expectedProps)
    }

    @Test
    fun `it finds idempotency token via resources`() {
        val model = """
            namespace com.test
            
            service ResourceService {
                resources: [Resource],
                version: "1"
            }
            resource Resource {
                operations: [CreateResource]
            }
            operation CreateResource {
                input: IdempotentInput
            }
            structure IdempotentInput {
                @idempotencyToken
                tok: String
            }
        """.toSmithyModel()

        model.expectShape<ServiceShape>("com.test#ResourceService").hasIdempotentTokenMember(model) shouldBe true
    }

    @Test
    fun `it imports references`() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = arrayOf(
            ClientConfigProperty {
                name = "complexProp"
                symbol = buildSymbol {
                    name = "ComplexType"
                    namespace = "test.complex"
                    reference(
                        buildSymbol { name = "SubTypeA"; namespace = "test.complex" },
                        SymbolReference.ContextOption.USE
                    )
                    reference(
                        buildSymbol { name = "SubTypeB"; namespace = "test.complex" },
                        SymbolReference.ContextOption.USE
                    )
                }
            }
        )

        ClientConfigGenerator(renderingCtx, detectDefaultProps = false, builderReturnType = null, *customProps).render()
        val contents = writer.toString()

        listOf(
            "test.complex.ComplexType",
            "test.complex.SubTypeA",
            "test.complex.SubTypeB",
        )
            .map { "import $it" }
            .forEach(contents::shouldContain)
    }

    @Test
    fun `it renders a companion object`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        ClientConfigGenerator(renderingCtx).render()
        val contents = writer.toString()

        contents.assertBalancedBracesAndParens()

        val expectedCompanion = """
    companion object {
        inline operator fun invoke(block: Builder.() -> kotlin.Unit): Config = Builder().apply(block).build()
    }
"""
        contents.shouldContainWithDiff(expectedCompanion)
    }

    @Test
    fun testPropertyTypesRenderCorrectly() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = arrayOf(
            ClientConfigProperty {
                name = "nullFoo"
                symbol = buildSymbol { name = "Foo" }
            },
            ClientConfigProperty {
                name = "defaultFoo"
                symbol = buildSymbol { name = "Foo"; defaultValue = "DefaultFoo"; nullable = false }
            },
            ClientConfigProperty {
                name = "constFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ClientConfigPropertyType.ConstantValue("ConstantFoo")
            },
            ClientConfigProperty {
                name = "requiredFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ClientConfigPropertyType.Required()
            },
            ClientConfigProperty {
                name = "requiredFoo2"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ClientConfigPropertyType.Required("override message")
            },
            ClientConfigProperty {
                name = "requiredDefaultedFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ClientConfigPropertyType.RequiredWithDefault("DefaultedFoo()")
            },
        )

        ClientConfigGenerator(renderingCtx, detectDefaultProps = false, builderReturnType = null, *customProps).render()
        val contents = writer.toString()

        val expectedProps = """
    val constFoo: Foo = ConstantFoo
    val defaultFoo: Foo = builder.defaultFoo
    val nullFoo: Foo? = builder.nullFoo
    val requiredDefaultedFoo: Foo = builder.requiredDefaultedFoo ?: DefaultedFoo()
    val requiredFoo: Foo = requireNotNull(builder.requiredFoo) { "requiredFoo is a required configuration property" }
    val requiredFoo2: Foo = requireNotNull(builder.requiredFoo2) { "override message" }
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedImplProps = """
        var defaultFoo: Foo = DefaultFoo
        var nullFoo: Foo? = null
        var requiredDefaultedFoo: Foo? = null
        var requiredFoo: Foo? = null
        var requiredFoo2: Foo? = null
"""
        contents.shouldContainWithDiff(expectedImplProps)
    }
}
