/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.createSymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

class WaiterGeneratorTest {
    val generated = generateService("waiter-tests.smithy")

    @Test
    fun testDefaultDelays() {
        val expected = """
            private val defaultDelaysRetryStrategy = run {
                val delayOptions = ExponentialBackoffWithJitterOptions(
                    initialDelayMs = 2_000,
                    scaleFactor = 1.5,
                    jitter = 1.0,
                    maxBackoffMs = 120_000,
                )
                val delay = ExponentialBackoffWithJitter(delayOptions)

                val waiterOptions = StandardRetryStrategyOptions(maxTimeMs = 300_000, maxAttempts = 20)
                StandardRetryStrategy(waiterOptions, NoOpTokenBucket, delay)
            }
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testCustomDelays() {
        val expected = """
            private val customDelaysRetryStrategy = run {
                val delayOptions = ExponentialBackoffWithJitterOptions(
                    initialDelayMs = 5_000,
                    scaleFactor = 1.5,
                    jitter = 1.0,
                    maxBackoffMs = 30_000,
                )
                val delay = ExponentialBackoffWithJitter(delayOptions)

                val waiterOptions = StandardRetryStrategyOptions(maxTimeMs = 300_000, maxAttempts = 20)
                StandardRetryStrategy(waiterOptions, NoOpTokenBucket, delay)
            }
        """.trimIndent()
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

        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        ctx.allWaiters().forEach(writer::renderWaiter)
        return writer.toString()
    }
}
