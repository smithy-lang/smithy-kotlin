/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.waiters

import aws.smithy.kotlin.codegen.KotlinCodegenPlugin
import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.GenerationContext
import aws.smithy.kotlin.codegen.core.KotlinDelegator
import aws.smithy.kotlin.codegen.loadModelFromResource
import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.createSymbolProvider
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
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
            public suspend fun TestClient.waitUntilFooOptionalExists(request: DescribeFooOptionalRequest = DescribeFooOptionalRequest { }, retryStrategy: RetryStrategy? = null): Outcome<DescribeFooOptionalResponse> {
        """.trimIndent()
        val methodFooter = """
                val policy = AcceptorRetryPolicy(request, acceptors)
                return strategy.retry(policy) { describeFooOptional(request) }
            }
        """.trimIndent()
        generateService("simple-service-with-operation-waiter.smithy").shouldContain(methodHeader, methodFooter)
    }

    @Test
    fun testWaiterSignatureWithRequiredInput() {
        val methodHeader = """
            /**
             * Wait until a foo exists with required input
             */
            public suspend fun TestClient.waitUntilFooRequiredExists(request: DescribeFooRequiredRequest, retryStrategy: RetryStrategy? = null): Outcome<DescribeFooRequiredResponse> {
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
            val strategy = retryStrategy ?: StandardRetryStrategy {
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

    private fun generateService(modelResourceName: String): String {
        val model = loadModelFromResource(modelResourceName)
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val service = model.getShape(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID)).get().asServiceShape().get()
        val settings = KotlinSettings(service.id, KotlinSettings.PackageSettings(TestModelDefault.NAMESPACE, TestModelDefault.MODEL_VERSION), sdkId = service.id.name)

        val manifest = MockManifest()
        val ctx = GenerationContext(model, provider, settings, protocolGenerator = null)
        val delegator = KotlinDelegator(ctx, manifest)

        val generator = ServiceWaitersGenerator()
        generator.writeAdditionalFiles(ctx, delegator)
        delegator.flushWriters()

        return manifest.expectFileString("src/main/kotlin/com/test/waiters/Waiters.kt")
    }
}
