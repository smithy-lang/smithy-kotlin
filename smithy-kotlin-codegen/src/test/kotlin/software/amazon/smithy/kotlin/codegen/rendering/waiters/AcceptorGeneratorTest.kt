/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
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

class AcceptorGeneratorTest {
    val generated = generateService("waiter-tests.smithy")

    @Test
    fun testSuccessAcceptors() {
        val expected = """
            private val successAcceptorsAcceptorList: List<Acceptor<DescribeFooRequest, DescribeFooResponse>> = listOf(
                SuccessAcceptor(RetryDirective.TerminateAndSucceed, true),
                SuccessAcceptor(RetryDirective.TerminateAndFail, false),
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testErrorTypeAcceptors() {
        val expected = """
            private val errorTypeAcceptorsAcceptorList: List<Acceptor<DescribeFooRequest, DescribeFooResponse>> = listOf(
                ErrorTypeAcceptor(RetryDirective.TerminateAndSucceed, SuccessError::class),
                ErrorTypeAcceptor(RetryDirective.RetryError(RetryErrorType.ServerSide), RetryError::class),
                ErrorTypeAcceptor(RetryDirective.TerminateAndFail, FailureError::class),
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testOutputAcceptor() {
        val expected = """
            private val outputAcceptorAcceptorList: List<Acceptor<DescribeFooRequest, DescribeFooResponse>> = listOf(
                OutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val name = it.name
                    name?.toString() == "foo"
                },
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testInputOutputAcceptors() {
        val expected = """
            private val inputOutputAcceptorsAcceptorList: List<Acceptor<DescribeFooRequest, DescribeFooResponse>> = listOf(
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val input = it.input
                    val id = input?.id
                    id?.toString() == "foo"
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val isDeprecated = output?.isDeprecated
                    isDeprecated == false
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val tags = output?.tags
                    (tags?.size ?: 0) > 1 && tags?.all { it?.toString() == "foo" }
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val tags = output?.tags
                    tags?.any { it?.toString() == "foo" } ?: false
                },
            )
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
        ctx.allWaiters().forEach(writer::renderAcceptorList)
        return writer.toString()
    }
}
