/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceWaitersGeneratorTest {
    @Test
    fun testServiceGate() {
        mapOf(
            "simple-service-with-operation-waiter.smithy" to true,
            "simple-service-with-resource-waiter.smithy" to true,
            "simple-service.smithy" to false,
        ).forEach { (modelName, expectEnabled) ->
            val model = loadModelFromResource(modelName)
            val settings = model.newTestContext().generationCtx.settings
            val actualEnabled = ServiceWaitersGenerator().enabledForService(model, settings)
            assertEquals(expectEnabled, actualEnabled)
        }
    }

    @Test
    fun testWaiterSignatureWithOptionalInput() {
        val methodHeader = """
            /**
             * Wait until a foo exists with optional input
             */
            public suspend fun TestClient.waitUntilFooOptionalExists(request: DescribeFooOptionalRequest = DescribeFooOptionalRequest { }): Outcome<DescribeFooOptionalResponse> {
        """.trimIndent()
        val methodFooter = """
                val policy = AcceptorRetryPolicy(request, acceptors)
                return strategy.retry(policy) { describeFooOptional(request) }
            }
        """.trimIndent()
        generateService().shouldContain(methodHeader, methodFooter)
    }

    @Test
    fun testWaiterSignatureWithRequiredInput() {
        val methodHeader = """
            /**
             * Wait until a foo exists with required input
             */
            public suspend fun TestClient.waitUntilFooRequiredExists(request: DescribeFooRequiredRequest): Outcome<DescribeFooRequiredResponse> {
        """.trimIndent()
        listOf(
            generateService("simple-service-with-operation-waiter.smithy"),
            generateService("simple-service-with-resource-waiter.smithy"),
        ).forEach { generated ->
            generated.shouldContain(methodHeader)
        }
    }

    @Test
    fun testConvenienceWaiterMethod() {
        val expected = """
            /**
             * Wait until a foo exists with required input
             */
            public suspend fun TestClient.waitUntilFooRequiredExists(block: DescribeFooRequiredRequest.Builder.() -> Unit): Outcome<DescribeFooRequiredResponse> =
                waitUntilFooRequiredExists(DescribeFooRequiredRequest.Builder().apply(block).build())
        """.trimIndent()
        listOf(
            generateService("simple-service-with-operation-waiter.smithy"),
            generateService("simple-service-with-resource-waiter.smithy"),
        ).forEach { generated ->
            generated.shouldContain(expected)
        }
    }

    @Test
    fun testAcceptorList() {
        val expected = """
            val acceptors = listOf<Acceptor<DescribeFooRequiredRequest, DescribeFooRequiredResponse>>(
                SuccessAcceptor(RetryDirective.TerminateAndSucceed, true),
                ErrorTypeAcceptor(RetryDirective.RetryError(RetryErrorType.ServerSide), "NotFound"),
            )
        """.formatForTest()
        listOf(
            generateService("simple-service-with-operation-waiter.smithy"),
            generateService("simple-service-with-resource-waiter.smithy"),
        ).forEach { generated ->
            generated.shouldContainOnlyOnce(expected)
        }
    }

    @Test
    fun testRetryStrategy() {
        val expected = """
            val strategy = StandardRetryStrategy {
                maxAttempts = 20
                tokenBucket = InfiniteTokenBucket
                delayProvider {
                    initialDelay = 2_000.milliseconds
                    scaleFactor = 1.5
                    jitter = 1.0
                    maxBackoff = 120_000.milliseconds
                }
            }
        """.formatForTest()
        listOf(
            generateService("simple-service-with-operation-waiter.smithy"),
            generateService("simple-service-with-resource-waiter.smithy"),
        ).forEach { generated ->
            generated.shouldContain(expected)
        }
    }

    private fun generateService(modelResourceName: String = "simple-service-with-operation-waiter.smithy"): String {
        val model = loadModelFromResource(modelResourceName)
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val service = model.getShape(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID)).get().asServiceShape().get()
        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings(TestModelDefault.NAMESPACE, TestModelDefault.MODEL_VERSION), sdkId = service.id.name)

        val ctx = object : CodegenContext {
            override val model: Model = model
            override val symbolProvider: SymbolProvider = provider
            override val settings: KotlinSettings = settings
            override val protocolGenerator: ProtocolGenerator? = null
            override val integrations: List<KotlinIntegration> = listOf()
        }

        val manifest = MockManifest()
        val delegator = KotlinDelegator(settings, model, manifest, provider)

        val generator = ServiceWaitersGenerator()
        generator.writeAdditionalFiles(ctx, delegator)
        delegator.flushWriters()

        return manifest.expectFileString("src/main/kotlin/com/test/waiters/Waiters.kt")
    }
}
