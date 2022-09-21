/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointTestCase

/**
 * Renders test cases for the default endpoint provider.
 */
class DefaultEndpointProviderTestGenerator(
    private val writer: KotlinWriter,
    private val rules: EndpointRuleSet,
    private val cases: List<EndpointTestCase>,
    private val providerSymbol: Symbol,
    private val paramsSymbol: Symbol,
) {
    companion object {
        const val CLASS_NAME = "DefaultEndpointProviderTest"

        fun getSymbol(ctx: CodegenContext): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${ctx.settings.pkg.name}.endpoints"
            }
    }

    val paramNames = rules.parameters.toList().map { it.name.asString() }

    val runTestSymbol = buildSymbol {
        name = "runTest"
        namespace = "kotlinx.coroutines.test"
    }

    fun render() {
        writer.addImport("*", namespace = "kotlin.test")
        writer.withBlock("public class #L {", "}", CLASS_NAME) {
            cases.forEachIndexed { index, it ->
                renderTestCase(index, it)
                write("")
            }
        }
    }

    private fun renderTestCase(index: Int, case: EndpointTestCase) {
        case.documentation.getOrNull()?.let {
            writer.write("// #L", it)
        }
        writer.write("@Test")
        writer.withBlock("fun test#L() = #T {", "}", index, runTestSymbol) {
            withBlock("val params = #T {", "}", paramsSymbol) {
                case.params.members.entries.forEach { (k, v) ->
                    // FIXME: externally-supplied rules currently have some extraneous params
                    // this check can be removed once we formally consume rules in the model
                    if (k.value !in paramNames) {
                        return@forEach
                    }

                    writeInline("#L = ", k.value.replaceFirstChar(Char::lowercase))
                    writeParamValue(v)
                    write("")
                }
            }
            renderTestCaseExpectation(case)
        }
    }

    private fun writeParamValue(v: Node) {
        when (v) {
            is StringNode -> writer.writeInline("#S", v.value)
            is BooleanNode -> writer.writeInline("#L", v.value)
            else -> throw IllegalArgumentException("unexpected test case param value")
        }
    }

    private fun renderTestCaseExpectation(case: EndpointTestCase) {
        if (case.expect.error.getOrNull() != null) {
            writer.withBlock("assertFailsWith<#T>(#S) {", "}", RuntimeTypes.Http.Endpoints.EndpointProviderException, case.expect.error.get()) {
                writer.write("#T().resolveEndpoint(params)", providerSymbol)
            }
            return
        }

        if (case.expect.endpoint.getOrNull() == null) {
            throw IllegalArgumentException("endpoint test case has neither an expected error nor endpoint")
        }

        val endpoint = case.expect.endpoint.get()

        writer.withBlock("val expected = #T(", ")", RuntimeTypes.Http.Endpoints.Endpoint) {
            write("#T.parse(#S)", RuntimeTypes.Http.Url, endpoint.url)
        }
        writer.write("val actual = #T().resolveEndpoint(params)", providerSymbol)
        writer.write("assertEquals(expected.uri, actual.uri)")
    }
}
