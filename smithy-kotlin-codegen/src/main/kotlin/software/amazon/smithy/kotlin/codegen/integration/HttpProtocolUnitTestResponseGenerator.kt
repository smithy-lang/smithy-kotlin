/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinDependency
import software.amazon.smithy.kotlin.codegen.ShapeValueGenerator
import software.amazon.smithy.kotlin.codegen.defaultName
import software.amazon.smithy.kotlin.codegen.hasStreamingMember
import software.amazon.smithy.model.shapes.*
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
        writer.addImport(KotlinDependency.CLIENT_RT_SMITHY_TEST.namespace, "*", "")
        writer.addImport(KotlinDependency.CLIENT_RT_HTTP.namespace, "HttpStatusCode", "")
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_SMITHY_TEST.dependencies)
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
                                writer.write("body = \"\"\"\$L\"\"\"", body)
                            }

                            if (test.bodyMediaType.isPresent) {
                                val bodyMediaType = test.bodyMediaType.get()
                                writer.write("bodyMediaType = \$S", bodyMediaType)
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

                    // invoke the DSL builder for the input type
                    writer.write("val input = ${inputSymbol.name}{}")
                }

                writer.openBlock("val service = \$L.build{", serviceName)
                    .write("httpEngine = mockEngine")
                    .closeBlock("}")

                renderServiceCall()
            }
            .closeBlock("}")
    }

    /**
     * invoke the service operation
     */
    protected open fun renderServiceCall() {
        val inputParamName = operation.input.map { "input" }.orElse("")
        val isStreamingRequest = operation.input.map {
            val inputShape = model.expectShape(it)
            inputShape.asStructureShape().get().hasStreamingMember(model)
        }.orElse(false)

        // invoke the operation
        val opName = operation.defaultName()

        if (operation.output.isPresent) {
            // streaming requests have a different operation signature that require a block to be passed to
            // process the response - add an empty block if necessary
            if (isStreamingRequest) {
                writer.openBlock("service.\$L(\$L){ actualResult ->", opName, inputParamName)
                    .call {
                        renderAssertions()
                    }
                    .closeBlock("}")
            } else {
                writer.write("val actualResult = service.\$L(\$L)", opName, inputParamName)
                renderAssertions()
            }
        } else {
            // no output...nothing to really assert...
            writer.write("service.\$L(\$L)", opName, inputParamName)
        }
    }

    protected fun renderAssertions() {
        val outputShape = outputShape ?: return
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "assertEquals", "")

        val members = outputShape.members()
        for (member in members) {
            val target = model.expectShape(member.target)
            val expMemberName = "expectedResult?.${member.defaultName()}"
            val actMemberName = "actualResult.${member.defaultName()}"
            when (target) {
                is BlobShape -> {
                    val suffix = if (target.hasTrait(StreamingTrait::class.java)) {
                        "?.toByteArray()"
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
                    writer.write("\$S to \$S$suffix", hdr.key, hdr.value)
                }
            }
            .closeBlock(")")
    }

    open class Builder : HttpProtocolUnitTestGenerator.Builder<HttpResponseTestCase>() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpResponseTestCase> {
            return HttpProtocolUnitTestResponseGenerator(this)
        }
    }
}
