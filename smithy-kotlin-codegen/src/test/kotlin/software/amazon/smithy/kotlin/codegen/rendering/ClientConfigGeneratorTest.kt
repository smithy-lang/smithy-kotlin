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
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.hasIdempotentTokenMember
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
class Config private constructor(builder: BuilderImpl): HttpClientConfig, IdempotencyTokenConfig, SdkClientConfig {
"""
        contents.shouldContain(expectedCtor)

        val expectedProps = """
    override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
    override val idempotencyTokenProvider: IdempotencyTokenProvider? = builder.idempotencyTokenProvider
    val retryStrategy: RetryStrategy = run {
        val strategyOptions = StandardRetryStrategyOptions.Default
        val tokenBucket = StandardRetryTokenBucket(StandardRetryTokenBucketOptions.Default)
        val delayer = ExponentialBackoffWithJitter(ExponentialBackoffWithJitterOptions.Default)
        StandardRetryStrategy(strategyOptions, tokenBucket, delayer)
    }
    override val sdkLogMode: SdkLogMode = builder.sdkLogMode
"""
        contents.shouldContain(expectedProps)

        val expectedJavaBuilderInterface = """
    interface FluentBuilder {
        fun httpClientEngine(httpClientEngine: HttpClientEngine): FluentBuilder
        fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): FluentBuilder
        fun sdkLogMode(sdkLogMode: SdkLogMode): FluentBuilder
        fun build(): Config
    }
"""
        contents.shouldContainOnlyOnceWithDiff(expectedJavaBuilderInterface)

        val expectedDslBuilderInterface = """
    interface DslBuilder {
        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc)
         */
        var httpClientEngine: HttpClientEngine?

        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        var idempotencyTokenProvider: IdempotencyTokenProvider?

        /**
         * Configure events that will be logged. By default clients will not output
         * raw requests or responses. Use this setting to opt-in to additional debug logging.
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        var sdkLogMode: SdkLogMode

        fun build(): Config
    }
"""
        contents.shouldContainWithDiff(expectedDslBuilderInterface)

        val expectedBuilderImpl = """
    internal class BuilderImpl() : FluentBuilder, DslBuilder {
        override var httpClientEngine: HttpClientEngine? = null
        override var idempotencyTokenProvider: IdempotencyTokenProvider? = null
        override var sdkLogMode: SdkLogMode = SdkLogMode.Default

        override fun build(): Config = Config(this)
        override fun httpClientEngine(httpClientEngine: HttpClientEngine): FluentBuilder = apply { this.httpClientEngine = httpClientEngine }
        override fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): FluentBuilder = apply { this.idempotencyTokenProvider = idempotencyTokenProvider }
        override fun sdkLogMode(sdkLogMode: SdkLogMode): FluentBuilder = apply { this.sdkLogMode = sdkLogMode }
    }
"""
        contents.shouldContainWithDiff(expectedBuilderImpl)

        val expectedImports = listOf(
            "import ${KotlinDependency.HTTP.namespace}.config.HttpClientConfig",
            "import ${KotlinDependency.HTTP.namespace}.engine.HttpClientEngine",
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
class Config private constructor(builder: BuilderImpl) {
"""
        contents.shouldContain(expectedCtor)

        val expectedProps = """
    val boolProp: Boolean? = builder.boolProp
    val intProp: Int = builder.intProp
    val nullIntProp: Int? = builder.nullIntProp
    val stringProp: String? = builder.stringProp
"""
        contents.shouldContain(expectedProps)

        val expectedDslProps = """
        override var boolProp: Boolean? = null
        override var intProp: Int = 1
        override var nullIntProp: Int? = null
        override var stringProp: String? = null
"""
        contents.shouldContain(expectedDslProps)
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
        @JvmStatic
        fun fluentBuilder(): FluentBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): Config = BuilderImpl().apply(block).build()
    }
"""
        contents.shouldContainWithDiff(expectedCompanion)
    }
}
