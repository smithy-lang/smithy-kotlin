/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase

/**
 * Generates HTTP protocol unit tests for `httpRequestTest` cases
 */
open class HttpProtocolUnitTestRequestGenerator protected constructor(builder: Builder) :
    HttpProtocolUnitTestGenerator<HttpRequestTestCase>(builder) {

    override fun openTestFunctionBlock(): String = "= httpRequestTest {"

    override fun renderTestBody(test: HttpRequestTestCase) {
        writer.addImport(KotlinDependency.CLIENT_RT_SMITHY_TEST.namespace, "*")
        writer.addImport(KotlinDependency.CLIENT_RT_HTTP.namespace, "HttpMethod")
        writer.addImport("${KotlinDependency.CLIENT_RT_CORE.namespace}.config", "IdempotencyTokenProvider")
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_SMITHY_TEST.dependencies)
        renderExpectedBlock(test)
        writer.write("")
        renderOperationBlock(test)
    }

    private fun renderExpectedBlock(test: HttpRequestTestCase) {
        writer.openBlock("expected {")
            .call {
                writer
                    .write("method = HttpMethod.${test.method.toUpperCase()}")
                    .write("uri = #S", test.uri)
                    .call {
                        test.resolvedHost.ifPresent { expectedHost ->
                            writer.write("resolvedHost = #S", expectedHost)
                        }
                    }
                    .call { renderExpectedQueryParams(test) }
                    .call { renderExpectedListOfParams("forbiddenQueryParams", test.forbidQueryParams) }
                    .call { renderExpectedListOfParams("requiredQueryParams", test.requireQueryParams) }
                    .call { renderExpectedHeaders(test) }
                    .call { renderExpectedListOfParams("forbiddenHeaders", test.forbidHeaders) }
                    .call { renderExpectedListOfParams("requiredHeaders", test.requireHeaders) }
                    .call {
                        test.body.ifPresent { body ->
                            var bodyMediaType = ""
                            if (test.bodyMediaType.isPresent) {
                                bodyMediaType = test.bodyMediaType.get()
                                writer.write("bodyMediaType = #S", bodyMediaType)
                            }

                            if (body.isBlank()) {
                                writer.write("bodyAssert = ::assertEmptyBody")
                            } else {
                                writer.write("body = \"\"\"#L\"\"\"", body)

                                val compareFunc = when (bodyMediaType.toLowerCase()) {
                                    "application/json" -> "::assertJsonBodiesEqual"
                                    "application/xml" -> "::assertXmlBodiesEqual"
                                    "application/x-www-form-urlencoded" -> "::assertFormUrlBodiesEqual"
                                    // compare reader bytes
                                    else -> "::assertBytesEqual"
                                }
                                writer.write("bodyAssert = $compareFunc")
                            }
                        }
                    }
                    .write("")
            }
            .closeBlock("}")
    }

    private fun renderOperationBlock(test: HttpRequestTestCase) {
        writer.openBlock("operation { mockEngine ->")
            .call {
                var inputParamName = ""
                var isStreamingRequest = false
                operation.input.ifPresent {
                    inputParamName = "input"
                    val inputShape = model.expectShape(it)

                    isStreamingRequest = inputShape.asStructureShape().get().hasStreamingMember(model)

                    // invoke the DSL builder for the input type
                    writer.writeInline("\nval input = ")
                        .indent()
                        .call {
                            ShapeValueGenerator(model, symbolProvider).writeShapeValueInline(writer, inputShape, test.params)
                        }
                        .dedent()
                        .write("")
                }

                val service = symbolProvider.toSymbol(serviceShape)
                writer.openBlock("val service = #L {", service.name)
                    .call { renderConfigureServiceClient(test) }
                    .closeBlock("}")

                // last statement should be service invoke
                val opName = operation.defaultName()

                // streaming requests have a different operation signature that require a block to be passed to
                // process the response - add an empty block if necessary
                val block = if (isStreamingRequest) "{}" else ""
                writer.write("service.#L(#L)$block", opName, inputParamName)
            }
            .closeBlock("}")
    }

    /**
     * Configure the service client before executing the request for the test. By default this function
     * configures a mock HttpClientEngine and an idempotency token generator appropriate for protocol tests.
     */
    open fun renderConfigureServiceClient(test: HttpRequestTestCase) {
        writer.write("httpClientEngine = mockEngine")
        if (idempotentFieldsInModel) {
            writer.write("idempotencyTokenProvider = IdempotencyTokenProvider { \"00000000-0000-4000-8000-000000000000\" }")
        }
    }

    private fun renderExpectedQueryParams(test: HttpRequestTestCase) {
        if (test.queryParams.isEmpty()) return

        val queryParams = test.queryParams
            .map {
                val kvPair = it.split("=", limit = 2)
                val value = kvPair.getOrNull(1) ?: ""
                Pair(kvPair[0], value)
            }

        writer.openBlock("queryParams = listOf(")
            .call {
                queryParams.forEachIndexed { idx, (key, value) ->
                    val suffix = if (idx < queryParams.size - 1) "," else ""
                    writer.write("#S to #S$suffix", key, value)
                }
            }
            .closeBlock(")")
    }

    private fun renderExpectedHeaders(test: HttpRequestTestCase) {
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

    private fun renderExpectedListOfParams(name: String, params: List<String>) {
        if (params.isEmpty()) return
        val joined = params.joinToString(
            separator = ",",
            transform = { "\"$it\"" }
        )
        writer.write("$name = listOf($joined)")
    }

    open class Builder : HttpProtocolUnitTestGenerator.Builder<HttpRequestTestCase>() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpRequestTestCase> =
            HttpProtocolUnitTestRequestGenerator(this)
    }
}
