/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.SerdeIndex
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborSerdeDescriptorGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.DeserializeStructGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.documentDeserializer
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Generates HTTP protocol unit tests for `httpRequestTest` cases
 */
open class HttpProtocolUnitTestRequestGenerator protected constructor(builder: Builder) : HttpProtocolUnitTestGenerator<HttpRequestTestCase>(builder) {

    object ConfigureServiceClient : SectionId {
        val Test: SectionKey<HttpRequestTestCase> = SectionKey("Test")
        val Context: SectionKey<ProtocolGenerator.GenerationContext> = SectionKey("Context")
        val Operation: SectionKey<OperationShape> = SectionKey("Operation")
    }

    protected open val inputShape: Shape?
        get() {
            return operation.input.map {
                model.expectShape(it)
            }.orElse(null)
        }

    override fun openTestFunctionBlock(): String = "= httpRequestTest {"

    override fun renderTestBody(test: HttpRequestTestCase) {
        writer.addImport(KotlinDependency.SMITHY_TEST.namespace, "*")
        writer.addImport(KotlinDependency.HTTP.namespace, "HttpMethod")
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "*")
        writer.dependencies.addAll(KotlinDependency.SMITHY_TEST.dependencies)
        writer.dependencies.addAll(KotlinDependency.KOTLIN_TEST.dependencies)
        if (test.body.isPresent) {
            renderBodyAssertFn(test)
        }
        renderExpectedBlock(test)
        writer.write("")
        renderOperationBlock(test)
    }

    private fun renderStructDeserializer(writer: KotlinWriter, shape: Shape): Symbol {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val deserializeFnName = "deserialize" + StringUtils.capitalize(symbol.name) + "Document"
        return shape.documentDeserializer(ctx.settings, symbol) { blockWriter ->
            blockWriter.withBlock("internal fun #L(deserializer: #T): #T {", "}", deserializeFnName, RuntimeTypes.Serde.Deserializer, symbol) {
                val renderingCtx = RenderingContext(blockWriter, shape, model, symbolProvider, ctx.settings)
                val descriptorGenerator = CborSerdeDescriptorGenerator(renderingCtx)

                val deserializeStructGenerator = DeserializeStructGenerator(
                    ctx,
                    shape.members().toMutableList(),
                    blockWriter,
                    TimestampFormatTrait.Format.EPOCH_SECONDS,
                )

                blockWriter.write("val builder = #T.Builder()", symbol)
                blockWriter.write("")

                blockWriter.call { descriptorGenerator.render() }
                blockWriter.call { deserializeStructGenerator.render() }

                blockWriter.write("")
                blockWriter.write("builder.correctErrors()")
                blockWriter.write("return builder.build()")
            }
        }.also {
            writer.addImport(it)
        }
    }

    /**
     * Render a custom bodyAssertFn rather than using a statically-defined one (such as `assertBytesEqual`).
     * This is useful when your body equality assertion needs to deserialize the raw bytes into real types.
     */
    private fun renderBodyAssertFn(test: HttpRequestTestCase) {
        if (!test.bodyMediaType.isPresent) {
            return
        }

        when (test.bodyMediaType.get()) {
            "application/cbor" -> {
                val inputShape = inputShape ?: return

                val serdeIndex = SerdeIndex.of(ctx.model)
                serdeIndex.requiresDocumentDeserializer(inputShape).forEach {
                    renderStructDeserializer(writer, it)
                }
                val inputDeserializer = renderStructDeserializer(writer, inputShape)

                writer.write("")
                writer.withBlock("suspend fun assertCborBodiesEqual(expected: #1T?, actual: #1T?) {", "}", RuntimeTypes.Http.HttpBody) {
                    write("val expectedBytes = expected?.#T()?.#T()", RuntimeTypes.Http.readAll, RuntimeTypes.Core.Text.Encoding.decodeBase64)
                    write("val actualBytes = actual?.#T()?.#T()", RuntimeTypes.Http.readAll, RuntimeTypes.Core.Text.Encoding.decodeBase64)
                    writer.withBlock("if (expectedBytes == null && actualBytes == null) {", "}") {
                        write("return")
                    }
                    write("requireNotNull(expectedBytes) { #S }", "expected application/cbor body cannot be null")
                    write("requireNotNull(actualBytes) { #S }", "actual application/cbor body cannot be null")

                    write("")
                    write("val expectedRequest = #L(#T(expectedBytes))", inputDeserializer.name, RuntimeTypes.Serde.SerdeCbor.CborDeserializer)
                    write("val actualRequest = #L(#T(expectedBytes))", inputDeserializer.name, RuntimeTypes.Serde.SerdeCbor.CborDeserializer)
                    write("assertEquals(expectedRequest, actualRequest)")
                }
                writer.write("")
            }
            else -> {}
        }
    }

    private fun renderExpectedBlock(test: HttpRequestTestCase) {
        writer.openBlock("expected {")
            .call {
                writer
                    .write("method = HttpMethod.${test.method.uppercase()}")
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

                                val compareFunc = when (bodyMediaType.lowercase()) {
                                    "application/json" -> "::assertJsonBodiesEqual"
                                    "application/xml" -> "::assertXmlBodiesEqual"
                                    "application/x-www-form-urlencoded" -> "::assertFormUrlBodiesEqual"
                                    "application/cbor" -> "::assertCborBodiesEqual"
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
                operation.input.ifPresent {
                    inputParamName = "input"
                    val inputShape = model.expectShape(it)

                    // invoke the DSL builder for the input type
                    writer.writeInline("\nval input = ")
                        .indent()
                        .call {
                            ShapeValueGenerator(model, symbolProvider).instantiateShapeInline(writer, inputShape, test.params)
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

                val isStreamingResponse = model.expectShape<StructureShape>(operation.output.get()).hasStreamingMember(model)

                // streaming responses have a different operation signature that require a block to be passed to
                // process the response - add an empty block if necessary
                val block = if (isStreamingResponse) "{}" else ""
                writer.write("service.#L(#L)$block", opName, inputParamName)
            }
            .closeBlock("}")
    }

    /**
     * Configure the service client before executing the request for the test. By default this function
     * configures a mock HttpClientEngine and an idempotency token generator appropriate for protocol tests.
     */
    open fun renderConfigureServiceClient(test: HttpRequestTestCase) {
        writer.declareSection(
            ConfigureServiceClient,
            mapOf(
                ConfigureServiceClient.Test to test,
                ConfigureServiceClient.Context to ctx,
                ConfigureServiceClient.Operation to operation,
            ),
        ) {
            writer.write("httpClient = mockEngine")
            if (idempotentFieldsInModel) {
                writer.write("idempotencyTokenProvider = #T { \"00000000-0000-4000-8000-000000000000\" }", RuntimeTypes.SmithyClient.IdempotencyTokenProvider)
            }

            val hostname = test.host.getOrNull() ?: "hostname"

            // if the model doesn't have endpoint rules we have to fill it in with a default
            if (!serviceShape.hasTrait<EndpointRuleSetTrait>()) {
                writer.write(
                    "endpointProvider = #T { #T(#S) }",
                    EndpointProviderGenerator.getSymbol(ctx.settings),
                    RuntimeTypes.SmithyClient.Endpoints.Endpoint,
                    "https://$hostname",
                )
            }
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
            transform = { "\"$it\"" },
        )
        writer.write("$name = listOf($joined)")
    }

    open class Builder : HttpProtocolUnitTestGenerator.Builder<HttpRequestTestCase>() {
        override fun build(): HttpProtocolUnitTestGenerator<HttpRequestTestCase> =
            HttpProtocolUnitTestRequestGenerator(this)
    }
}
