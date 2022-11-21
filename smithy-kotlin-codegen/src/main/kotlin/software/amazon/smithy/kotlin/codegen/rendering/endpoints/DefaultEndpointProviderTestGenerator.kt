/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.expr.Expression
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
    private val expectedPropertyRenderers: Map<String, EndpointPropertyRenderer> = emptyMap(),
) : ExpressionRenderer {
    companion object {
        const val CLASS_NAME = "DefaultEndpointProviderTest"

        fun getSymbol(settings: KotlinSettings): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${settings.pkg.name}.endpoints"
            }
    }

    private val expressionGenerator = ExpressionGenerator(writer, rules, emptyMap()) // functions can't be referenced in property declarations

    private val paramNames = rules.parameters.toList().map { it.name.asString() }

    private val runTestSymbol = buildSymbol {
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

    override fun renderExpression(expr: Expression) {
        expr.accept(expressionGenerator)
    }

    private fun renderTestCase(index: Int, case: EndpointTestCase) {
        case.documentation.ifPresent {
            writer.write("// #L", it)
        }
        writer.write("@Test")
        writer.withBlock("fun test#L() = #T {", "}", index, runTestSymbol) {
            withBlock("val params = #T {", "}", paramsSymbol) {
                case.params.members.entries.forEach { (k, v) ->
                    // FIXME: externally-supplied rules currently have some extraneous params
                    // this check can be removed once those are removed / validated
                    if (k.value !in paramNames) {
                        return@forEach
                    }

                    writeInline("#L = ", k.value.toCamelCase())
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
        if (case.expect.error.isPresent) {
            writer.withBlock("val ex = assertFailsWith<#T> {", "}", RuntimeTypes.Http.Endpoints.EndpointProviderException) {
                write("#T().resolveEndpoint(params)", providerSymbol)
            }
            writer.write("assertEquals(#S, ex.message)", case.expect.error.get())
            return
        }

        val endpoint = case.expect.endpoint.orElseThrow {
            CodegenException("endpoint test case has neither an expected error nor endpoint")
        }

        writer.withBlock("val expected = #T(", ")", RuntimeTypes.Http.Endpoints.Endpoint) {
            write("uri = #T.parse(#S),", RuntimeTypes.Http.Url, endpoint.url)

            if (endpoint.headers.isNotEmpty()) {
                withBlock("headers = #T {", "},", RuntimeTypes.Http.Headers) {
                    endpoint.headers.entries.forEach { (k, v) ->
                        v.forEach {
                            write("append(#S, #S)", k, it)
                        }
                    }
                }
            }

            if (endpoint.properties.isNotEmpty()) {
                withBlock("attributes = #T().apply {", "},", RuntimeTypes.Utils.Attributes) {
                    endpoint.properties.entries.forEach { (k, v) ->
                        if (k in expectedPropertyRenderers) {
                            expectedPropertyRenderers[k]!!(writer, Expression.fromNode(v), this@DefaultEndpointProviderTestGenerator)
                            return@forEach
                        }

                        withBlock("set(", ")") {
                            write("#T(#S),", RuntimeTypes.Utils.AttributeKey, k)
                            renderExpression(Expression.fromNode(v))
                            write(",")
                        }
                    }
                }
            }
        }

        writer.write("val actual = #T().resolveEndpoint(params)", providerSymbol)
        writer.write("assertEquals(expected, actual)")
    }
}
