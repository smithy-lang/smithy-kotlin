/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.waiters

import aws.smithy.kotlin.codegen.KotlinCodegenPlugin
import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.loadModelFromResource
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.createSymbolProvider
import aws.smithy.kotlin.codegen.test.toSmithyModel
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

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
                    val name = it.name
                    name == "foo"
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
                    val input = it.input
                    val id = input.id
                    id == "foo"
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val isDeprecated = output.isDeprecated
                    isDeprecated == false
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val tags = output.tags
                    !(tags as List<String?>).isNullOrEmpty() && tags.all { it == "foo" }
                },
                InputOutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val output = it.output
                    val tags = output.tags
                    (tags as List<String?>?)?.any { it == "foo" } ?: false
                },
            )
        """.trimIndent()
        generated.shouldContainOnlyOnce(expected)
    }

    @Test
    fun testNestedOperationOutputMemberIsNullable() {
        // GetSummaryResponse.foo targets another operation's output (normalized to the same synthetic shape). Unlike
        // the input/output of an inputOutput acceptor, that member is an ordinary nullable member.
        val model = """
            namespace com.test

            use smithy.waiters#waitable

            service Test {
                version: "1.0.0",
                operations: [GetFoo, GetSummary]
            }

            operation GetFoo {
                output: GetFooResponse
            }

            @waitable(
                FooReady: {
                    acceptors: [
                        {
                            state: "success",
                            matcher: {
                                output: {
                                    path: "foo.status",
                                    expected: "ready",
                                    comparator: "stringEquals"
                                }
                            }
                        }
                    ]
                }
            )
            operation GetSummary {
                output: GetSummaryResponse
            }

            structure GetFooResponse {
                status: String
            }

            structure GetSummaryResponse {
                foo: GetFooResponse
            }
        """.toSmithyModel()

        val expected = """
            val $acceptorListName = listOf<Acceptor<GetSummaryRequest, GetSummaryResponse>>(
                OutputAcceptor(RetryDirective.TerminateAndSucceed) {
                    val foo = it.foo
                    val status = foo?.status
                    status == "ready"
                },
            )
        """.trimIndent()
        generateService(model).shouldContainOnlyOnce(expected)
    }

    private fun generateService(modelResourceName: String): String = generateService(loadModelFromResource(modelResourceName))

    private fun generateService(model: Model): String {
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
