/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.shape
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase

/**
 * Generates HTTP protocol unit tests for `httpResponseTest` cases
 */
open class HttpProtocolUnitTestResponseGenerator protected constructor(builder: Builder) :
    HttpProtocolUnitTestGenerator<HttpResponseTestCase>(builder) {

    protected open val outputShape: Shape?
        get() {
            return operation.output.map {
                model.expectShape(it)
            }.orElse(null)
        }

    protected val responseSymbol: Symbol?
        get() = outputShape?.let { symbolProvider.toSymbol(it) }

    override fun openTestFunctionBlock(): String {
        val respType = responseSymbol?.name ?: "Unit"
        return "= httpResponseTest<$respType> {"
    }

    override fun renderTestBody(test: HttpResponseTestCase) {
        writer.addImport(KotlinDependency.SMITHY_TEST.namespace, "*")
        writer.addImport(KotlinDependency.HTTP.namespace, "HttpStatusCode")
        writer.dependencies.addAll(KotlinDependency.SMITHY_TEST.dependencies)
        renderExpectedBlock(test)
        writer.write("")
        renderTestBlock(test)
    }

    private fun renderExpectedBlock(test: HttpResponseTestCase) {
        writer.openBlock("expected {")
            .call {
                writer
                    .write("statusCode = HttpStatusCode.fromValue(${test.code})")
                    .call { renderExpectedHeaders(test) }
                    .call {
                        test.body.ifPresent { body ->
                            if (body.isNotBlank()) {
                                writer.write("body = \"\"\"#L\"\"\"", body)
                            }

                            if (test.bodyMediaType.isPresent) {
                                val bodyMediaType = test.bodyMediaType.get()
                                writer.write("bodyMediaType = #S", bodyMediaType)
                            }
                        }
                    }
                    .call {
                        outputShape?.let {
                            writer.writeInline("\nresponse = ")
                            ShapeValueGenerator(model, symbolProvider).writeShapeValueInline(writer, it, test.params)
                        }
                    }
                    .write("")
            }
            .closeBlock("}")
    }

    protected open fun renderTestBlock(test: HttpResponseTestCase) {
        writer.openBlock("test { expectedResult, mockEngine ->")
            .call {
                operation.input.ifPresent {
                    val inputShape = model.expectShape(it)
                    val inputSymbol = symbolProvider.toSymbol(inputShape)

                    val labelMembers = inputShape.members().filter { it.hasTrait<HttpLabelTrait>() }

                    // invoke the DSL builder for the input type
                    writer.withBlock("val input = #T {", "}", inputSymbol) {
                        labelMembers.forEach { memberShape ->
                            val memberSymbol = symbolProvider.toSymbol(memberShape)
                            write("#L = #L", memberShape.defaultName(), memberSymbol.defaultUnboxedValue())
                        }
                    }
                }

                val service = symbolProvider.toSymbol(serviceShape)
                writer.openBlock("val service = #L {", service.name)
                    .call { renderConfigureServiceClient(test) }
                    .closeBlock("}")

                renderServiceCall()
            }
            .closeBlock("}")
    }

    /**
     * Configure the service client before executing the request for the test. By default this function
     * configures a mock HttpClientEngine and an idempotency token generator appropriate for protocol tests.
     */
    open fun renderConfigureServiceClient(test: HttpResponseTestCase) {
        writer.write("httpClient = mockEngine")
        if (idempotentFieldsInModel) {
            // see: https://awslabs.github.io/smithy/1.0/spec/http-protocol-compliance-tests.html#parameter-format
            writer.write("idempotencyTokenProvider = #T { \"00000000-0000-4000-8000-000000000000\" }", RuntimeTypes.SmithyClient.IdempotencyTokenProvider)
        }
    }

    /**
     * invoke the service operation
     */
    protected open fun renderServiceCall() {
        val inputParamName = operation.input.map { "input" }.orElse("")
        val isStreamingResponse = operation.output.map {
            val outputShape = model.expectShape(it)
            outputShape.asStructureShape().get().hasStreamingMember(model)
        }.orElse(false)

        // invoke the operation
        val opName = operation.defaultName()

        if (operation.output.isPresent) {
            // streaming responses have a different operation signature that require a block to be passed to
            // process the response - add an empty block if necessary
            if (isStreamingResponse) {
                writer.openBlock("service.#L(#L){ actualResult ->", opName, inputParamName)
                    .call {
                        renderAssertions()
                    }
                    .closeBlock("}")
            } else {
                writer.write("val actualResult = service.#L(#L)", opName, inputParamName)
                renderAssertions()
            }
        } else {
            // no output...nothing to really assert...
            writer.write("service.#L(#L)", opName, inputParamName)
        }
    }

    protected fun renderAssertions() {
        val outputShape = outputShape ?: return
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "assertEquals")

        val members = outputShape.members()
        for (member in members) {
            val target = model.expectShape(member.target)
            val expMemberName = "expectedResult?.${member.defaultName()}"
            val actMemberName = "actualResult.${member.defaultName()}"
            when (target) {
                is BlobShape -> {
                    val suffix = if (target.hasTrait<StreamingTrait>()) {
                        writer.format("?.#T()", RuntimeTypes.Core.Content.toByteArray)
                    } else {
                        ""
                    }
                    writer.write("assertBytesEqual($expMemberName$suffix, $actMemberName$suffix)")
                }
                else -> writer.write("assertEquals($expMemberName, $actMemberName)")
            }
        }
    }

    private fun renderExpectedHeaders(test: HttpResponseTestCase) {
        if (test.headers.isEmpty()) return
        writer.openBlock("headers = mapOf(")
            .call {
                for ((idx, hdr) in test.headers.entries.withIndex()) {
                    val suffix = if (idx < test.headers.size - 1) "," else ""
                    writer.write("#S to #S$suffix", hdr.key, hdr.value)
                }
            }
            .closeBlock(")")
    }

    open class Builder : HttpProtocolUnitTestGenerator.Builder<HttpResponseTestCase>() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpResponseTestCase> =
            HttpProtocolUnitTestResponseGenerator(this)
    }
}

private fun Symbol.defaultUnboxedValue(): String = when (shape) {
    is LongShape -> "0L"
    is FloatShape -> "0.0f"
    is DoubleShape -> "0.0"
    is NumberShape -> "0"
    is StringShape -> "\"\""
    is BooleanShape -> "false"
    else -> throw CodegenException("Cannot determine default value for unsupported shape $shape")
}
