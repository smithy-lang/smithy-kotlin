/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.pt

import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolUnitTestGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolUnitTestResponseGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.pt.renderTestCaseEpilogue
import software.amazon.smithy.kotlin.codegen.rendering.protocol.pt.renderTestCasePrelude
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase

/**
 * Renders a class with all the defined protocol tests for responses.
 */
open class ProtocolTestResponseGenerator protected constructor(builder: Builder) :
    HttpProtocolUnitTestResponseGenerator(builder) {

    /**
     * Render a test class and unit tests for the specified [testCases]
     */
    override fun renderTestClass(testClassName: String) {
        writer.addImport("${ctx.settings.pkg.name}", "*")

        writer.write("")
            .openBlock("internal class $testClassName(val results: MutableList<TestResult>) {")
            .openBlock("public fun runAll() {")
            .call {
                for (test in testCases) {
                    renderTestFunctionCall(test)
                }
            }
            .closeBlock("}")
            .call {
                for (test in testCases) {
                    renderTestFunction(test)
                }
            }
            .closeBlock("}")
    }

    /**
     * Write a single unit test function using the given [writer]
     */
    override fun renderTestFunction(test: HttpResponseTestCase) {
        test.documentation.ifPresent {
            writer.dokka(it)
        }

        writer.openBlock("private fun `${test.id}`() {")
            .call { renderTestCasePrelude(writer, test.id, "RESPONSE") }
            .openBlock(openTestBlock())
            .call { renderTestBody(test) }
            .closeBlock("}")
            .call { renderTestCaseEpilogue(writer) }
            .closeBlock("}")
    }

    fun renderTestFunctionCall(test: HttpResponseTestCase) {
        writer.write("`${test.id}`()")
    }

    fun openTestBlock(): String {
        val respType = responseSymbol?.name ?: "Unit"
        return "httpResponseTest<$respType> {"
    }

    open class Builder : HttpProtocolUnitTestResponseGenerator.Builder() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpResponseTestCase> =
            ProtocolTestResponseGenerator(this)
    }
}
