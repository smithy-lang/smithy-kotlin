/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceWaitersGeneratorTest {
    private val generated = generateService("simple-service-with-waiter.smithy")

    @Test
    fun testServiceGate() {
        val enabledServiceModel = loadModelFromResource("simple-service-with-waiter.smithy")
        val enabledServiceSettings = enabledServiceModel.newTestContext().generationCtx.settings
        assertTrue(ServiceWaitersGenerator().enabledForService(enabledServiceModel, enabledServiceSettings))

        val disabledServiceModel = loadModelFromResource("simple-service.smithy")
        val disabledServiceSettings = disabledServiceModel.newTestContext().generationCtx.settings
        assertFalse(ServiceWaitersGenerator().enabledForService(disabledServiceModel, disabledServiceSettings))
    }

    @Test
    fun testMainWaiterMethod() {
        val methodHeader = """
            /**
             * Wait until a foo exists
             */
            public suspend fun TestClient.waitUntilFooExists(request: DescribeFooRequest): Outcome<DescribeFooResponse> {
        """.trimIndent()
        val methodFooter = """
                val policy = AcceptorRetryPolicy(request, acceptors)
                return strategy.retry(policy) { describeFoo(request) }
            }
        """.trimIndent()
        generated.shouldContain(methodHeader, methodFooter)
    }

    @Test
    fun testConvenienceWaiterMethod() {
        val expected = """
            /**
             * Wait until a foo exists
             */
            public suspend fun TestClient.waitUntilFooExists(block: DescribeFooRequest.Builder.() -> Unit): Outcome<DescribeFooResponse> =
                waitUntilFooExists(DescribeFooRequest.Builder().apply(block).build())
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testAcceptorList() {
        val expected = """
            val acceptors = listOf<Acceptor<DescribeFooRequest, DescribeFooResponse>>(
                SuccessAcceptor(RetryDirective.TerminateAndSucceed, true),
                ErrorTypeAcceptor(RetryDirective.RetryError(RetryErrorType.ServerSide), "NotFound"),
            )
        """.formatForTest()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testRetryStrategy() {
        val expected = """
            val strategy = run {
                val delayOptions = ExponentialBackoffWithJitterOptions(
                    initialDelay = 2_000.milliseconds,
                    scaleFactor = 1.5,
                    jitter = 1.0,
                    maxBackoff = 120_000.milliseconds,
                )
                val delay = ExponentialBackoffWithJitter(delayOptions)
            
                val waiterOptions = StandardRetryStrategyOptions(maxAttempts = 20)
                StandardRetryStrategy(waiterOptions, InfiniteTokenBucket, delay)
            }
        """.formatForTest()
        generated.shouldContainOnlyOnce(expected)
    }

    private fun generateService(modelResourceName: String): String {
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
