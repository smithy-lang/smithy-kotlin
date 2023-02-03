/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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
    val acceptorListName = "acceptorList"
    val generated = generateService("waiter-tests.smithy")

    @Test
    fun testSuccessAcceptors() {
        val expected = """
            val $acceptorListName = listOf<Acceptor<DescribeFooRequest, DescribeFooResponse>>(
                SuccessAcceptor(RetryDirective.TerminateAndSucceed, true),
                SuccessAcceptor(RetryDirective.TerminateAndFail, false),
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testErrorTypeAcceptors() {
        val expected = """
            val $acceptorListName = listOf<Acceptor<DescribeFooRequest, DescribeFooResponse>>(
                ErrorTypeAcceptor(RetryDirective.TerminateAndSucceed, "SuccessError"),
                ErrorTypeAcceptor(RetryDirective.RetryError(RetryErrorType.ServerSide), "RetryError"),
                ErrorTypeAcceptor(RetryDirective.TerminateAndFail, "FailureError"),
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testOutputAcceptor() {
        val expected = """
            val $acceptorListName = listOf<Acceptor<DescribeFooRequest, DescribeFooResponse>>(
                OutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val name = it?.name
                    name?.toString() == "foo"
                },
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testInputOutputAcceptors() {
        val expected = """
            val $acceptorListName = listOf<Acceptor<DescribeFooRequest, DescribeFooResponse>>(
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val input = it?.input
                    val id = input?.id
                    id?.toString() == "foo"
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it?.output
                    val isDeprecated = output?.isDeprecated
                    isDeprecated == false
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it?.output
                    val tags = output?.tags
                    tags != null && tags.size > 0 && tags.all { it?.toString() == "foo" }
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it?.output
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
        ctx.allWaiters().forEach { writer.renderAcceptorList(it, acceptorListName) }
        return writer.toString()
    }
}
