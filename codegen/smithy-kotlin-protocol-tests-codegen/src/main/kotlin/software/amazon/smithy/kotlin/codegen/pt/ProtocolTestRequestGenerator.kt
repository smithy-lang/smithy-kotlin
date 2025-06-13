/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.pt

import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolUnitTestGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpProtocolUnitTestRequestGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.pt.renderTestCaseEpilogue
import software.amazon.smithy.kotlin.codegen.rendering.protocol.pt.renderTestCasePrelude
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase

/**
 * Renders a class with all the defined protocol tests for requests.
 */
open class ProtocolTestRequestGenerator protected constructor(builder: Builder) : HttpProtocolUnitTestRequestGenerator(builder) {

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
    override fun renderTestFunction(test: HttpRequestTestCase) {
        test.documentation.ifPresent {
            writer.dokka(it)
        }

        writer.openBlock("private fun `${test.id}`() {")
            .call { renderTestCasePrelude(writer, test.id, "REQUEST") }
            .openBlock("httpRequestTest {")
            .call { renderTestBody(test) }
            .closeBlock("}")
            .call { renderTestCaseEpilogue(writer) }
            .closeBlock("}")
    }

    fun renderTestFunctionCall(test: HttpRequestTestCase) {
        writer.write("`${test.id}`()")
    }

    open class Builder : HttpProtocolUnitTestRequestGenerator.Builder() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpRequestTestCase> =
            ProtocolTestRequestGenerator(this)
    }
}
