/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.CodegenContext
import software.amazon.smithy.kotlin.codegen.KotlinDependency
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.ext.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape

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
class Config private constructor(builder: BuilderImpl): HttpClientConfig, IdempotencyTokenConfig {
"""
        contents.shouldContain(expectedCtor)

        val expectedProps = """
    override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
    override val idempotencyTokenProvider: IdempotencyTokenProvider? = builder.idempotencyTokenProvider
"""
        contents.shouldContain(expectedProps)

        val expectedJavaBuilderInterface = """
    interface Builder {
        fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder
        fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder
        fun build(): Config
    }
"""
        contents.shouldContainOnlyOnceWithDiff(expectedJavaBuilderInterface)

        val expectedDslBuilderInterface = """
    interface DslBuilder {
        /**
         * Override the default HTTP client configuration (e.g. configure proxy behavior, concurrency, etc)
         */
        var httpClientEngine: HttpClientEngine?

        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        var idempotencyTokenProvider: IdempotencyTokenProvider?

        fun build(): Config
    }
"""
        contents.shouldContain(expectedDslBuilderInterface)

        val expectedBuilderImpl = """
    internal class BuilderImpl() : Builder, DslBuilder {
        override var httpClientEngine: HttpClientEngine? = null
        override var idempotencyTokenProvider: IdempotencyTokenProvider? = null

        override fun build(): Config = Config(this)
        override fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder = apply { this.httpClientEngine = httpClientEngine }
        override fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder = apply { this.idempotencyTokenProvider = idempotencyTokenProvider }
    }
"""
        contents.shouldContain(expectedBuilderImpl)

        val expectedImports = listOf(
            "import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.config.HttpClientConfig",
            "import ${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine.HttpClientEngine",
            "import ${KotlinDependency.CLIENT_RT_CORE.namespace}.config.IdempotencyTokenConfig",
            "import ${KotlinDependency.CLIENT_RT_CORE.namespace}.config.IdempotencyTokenProvider",
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

        ClientConfigGenerator(renderingCtx, detectDefaultProps = false, *customProps).render()
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
}
