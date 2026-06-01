/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.model.shape
import aws.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import aws.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait

/**
 * Generates benchmark classes for protocol test cases tagged with "serde-benchmark".
 * Uses a custom timing harness with interceptor-based measurement instead of JMH,
 * capturing precise serialization/deserialization timestamps at the SDK boundary.
 */
class HttpProtocolSerdeBenchmarkGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: KotlinWriter,
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val operation: OperationShape,
    private val serviceShape: ServiceShape,
) {
    private val serviceSymbol = symbolProvider.toSymbol(serviceShape)
    private val opName = operation.defaultName()

    private val idempotentFieldsInModel: Boolean by lazy {
        operation.input.isPresent &&
            model.expectShape(operation.input.get()).members().any { it.hasTrait(IdempotencyTokenTrait.ID.name) }
    }

    fun renderRequestBenchmarkClass(className: String, testCases: List<HttpRequestTestCase>) {
        writeImports()

        writer.write("")
        writer.openBlock("class $className {")
        writer.write("")
        writer.write("private val interceptor = BenchmarkInterceptor()")
        writer.write("")

        // Client with TestEngine returning 200 OK (no exception needed)
        writer.openBlock("private val client = ${serviceSymbol.name} {")
        writer.write("httpClient = #T { _, request ->", RuntimeTypes.HttpTest.TestEngine)
        if (isRpcV2Cbor()) {
            writer.write("    val respHeaders = #T().apply { append(\"smithy-protocol\", \"rpc-v2-cbor\") }.build()", RuntimeTypes.Http.HeadersBuilder)
            writer.write("    val resp = #T(#T.OK, respHeaders, #T.Empty)", RuntimeTypes.Http.Response.HttpResponse, RuntimeTypes.Http.StatusCode, RuntimeTypes.Http.HttpBody)
        } else {
            writer.write("    val resp = #T(#T.OK, #T.Empty, #T.Empty)", RuntimeTypes.Http.Response.HttpResponse, RuntimeTypes.Http.StatusCode, RuntimeTypes.Http.Headers, RuntimeTypes.Http.HttpBody)
        }
        writer.write("    val now = #T.now()", RuntimeTypes.Core.Instant)
        writer.write("    #T(request, resp, now, now, callContext())", RuntimeTypes.Http.HttpCall)
        writer.write("}")
        renderClientConfig()
        writer.write("interceptors.add(interceptor)")
        writer.closeBlock("}")

        // Pre-constructed inputs (outside timed loop)
        for (testCase in testCases) {
            renderInputField(testCase)
        }

        // suspend fun benchmarks(): List<BenchmarkResult>
        writer.write("")
        writer.openBlock("suspend fun benchmarks(): List<BenchmarkResult> {")
        writer.write("val results = mutableListOf<BenchmarkResult>()")
        for (testCase in testCases) {
            val fieldName = "input_${sanitizeName(testCase.id)}"
            writer.openBlock("results.add(BenchmarkHarness.run(")
            writer.write("id = #S,", testCase.id)
            writer.write("interceptor = interceptor,")
            writer.write("extractNanos = BenchmarkInterceptor::serializationNanos,")
            writer.closeBlock(") { client.#L($fieldName) })", opName)
        }
        writer.write("return results")
        writer.closeBlock("}")

        writer.closeBlock("}")

        // main() function
        writer.write("")
        writer.openBlock("fun main() = kotlinx.coroutines.runBlocking {")
        writer.write("val benchmark = $className()")
        writer.write("val results = benchmark.benchmarks()")
        writer.write("val metadata = BenchmarkMetadata(smithyKotlinVersion = #S, sdkVersion = #S)", "SNAPSHOT", "SNAPSHOT")
        writer.write("println(BenchmarkHarness.toJson(metadata, results))")
        writer.closeBlock("}")
    }

    fun renderResponseBenchmarkClass(className: String, testCases: List<HttpResponseTestCase>) {
        writeImports()

        writer.write("")
        writer.openBlock("class $className {")
        writer.write("")
        writer.write("private val interceptor = BenchmarkInterceptor()")

        // Per-test-case clients with canned responses
        for (testCase in testCases) {
            renderResponseClientField(testCase)
        }

        // Pre-constructed inputs (outside timed loop)
        if (operation.input.isPresent) {
            val inputShape = model.expectShape<StructureShape>(operation.input.get())
            val inputSymbol = symbolProvider.toSymbol(inputShape)
            val requiredMembers = inputShape.members().filter { it.isRequired }
            writer.write("")
            writer.openBlock("private val input = #T {", inputSymbol)
            requiredMembers.forEach { member ->
                val memberSymbol = symbolProvider.toSymbol(member)
                val defaultValue = memberSymbol.defaultUnboxedValue(writer)
                if (defaultValue != null) {
                    writer.write("#L = #L", member.defaultName(), defaultValue)
                }
            }
            writer.closeBlock("}")
        }

        // suspend fun benchmarks(): List<BenchmarkResult>
        writer.write("")
        writer.openBlock("suspend fun benchmarks(): List<BenchmarkResult> {")
        writer.write("val results = mutableListOf<BenchmarkResult>()")
        for (testCase in testCases) {
            val clientField = "client_${sanitizeName(testCase.id)}"
            val inputArg = if (operation.input.isPresent) "input" else ""
            writer.openBlock("results.add(BenchmarkHarness.run(")
            writer.write("id = #S,", testCase.id)
            writer.write("interceptor = interceptor,")
            writer.write("extractNanos = BenchmarkInterceptor::deserializationNanos,")
            writer.closeBlock(") { $clientField.#L($inputArg) })", opName)
        }
        writer.write("return results")
        writer.closeBlock("}")

        writer.closeBlock("}")

        // main() function
        writer.write("")
        writer.openBlock("fun main() = kotlinx.coroutines.runBlocking {")
        writer.write("val benchmark = $className()")
        writer.write("val results = benchmark.benchmarks()")
        writer.write("val metadata = BenchmarkMetadata(smithyKotlinVersion = #S, sdkVersion = #S)", "SNAPSHOT", "SNAPSHOT")
        writer.write("println(BenchmarkHarness.toJson(metadata, results))")
        writer.closeBlock("}")
    }

    private fun writeImports() {
        writer.write("import aws.sdk.kotlin.benchmarks.serde.BenchmarkHarness")
        writer.write("import aws.sdk.kotlin.benchmarks.serde.BenchmarkInterceptor")
        writer.write("import aws.sdk.kotlin.benchmarks.serde.BenchmarkMetadata")
        writer.write("import aws.sdk.kotlin.benchmarks.serde.BenchmarkResult")
        writer.write("import aws.smithy.kotlin.runtime.http.engine.callContext")
        writer.write("import aws.smithy.kotlin.runtime.smithy.test.encodeAsByteArray")
    }

    private fun renderClientConfig() {
        writer.write("region = \"us-east-1\"")
        writer.write("credentialsProvider = aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider {")
        writer.write("    accessKeyId = \"BENCHMARK\"")
        writer.write("    secretAccessKey = \"BENCHMARK\"")
        writer.write("}")
        if (idempotentFieldsInModel) {
            writer.write(
                "idempotencyTokenProvider = #T { \"00000000-0000-4000-8000-000000000000\" }",
                RuntimeTypes.SmithyClient.IdempotencyTokenProvider,
            )
        }
        if (!serviceShape.hasTrait<EndpointRuleSetTrait>()) {
            writer.write(
                "endpointProvider = #T { #T(#S) }",
                EndpointProviderGenerator.getSymbol(ctx.settings),
                RuntimeTypes.SmithyClient.Endpoints.Endpoint,
                "https://localhost",
            )
        }
    }

    private fun renderInputField(testCase: HttpRequestTestCase) {
        val fieldName = "input_${sanitizeName(testCase.id)}"

        if (operation.input.isPresent) {
            val inputShape = model.expectShape<StructureShape>(operation.input.get())
            writer.write("")
            writer.writeInline("private val $fieldName = ")
                .indent()
                .call {
                    ShapeValueGenerator(model, symbolProvider, explicitReceiver = true)
                        .instantiateShapeInline(writer, inputShape, testCase.params)
                }
                .dedent()
                .write("")
        }
    }

    private fun renderResponseClientField(testCase: HttpResponseTestCase) {
        val fieldName = "client_${sanitizeName(testCase.id)}"
        val bodyFieldName = "respBody_${sanitizeName(testCase.id)}"

        val body = testCase.body.orElse("").trim()
        writer.write("")
        if (body.isNotBlank()) {
            val isCborProtocol = testCase.protocol.name == "rpcv2Cbor"
            if (isCborProtocol) {
                writer.write(
                    "private val #L = #S.#T()",
                    bodyFieldName,
                    body,
                    RuntimeTypes.Core.Text.Encoding.decodeBase64Bytes,
                )
            } else {
                writer.write("private val #L = #S.encodeToByteArray()", bodyFieldName, body)
            }
        }

        writer.openBlock("private val $fieldName = ${serviceSymbol.name} {")

        writer.openBlock("httpClient = #T { _, request ->", RuntimeTypes.HttpTest.TestEngine)

        if (testCase.headers.isNotEmpty()) {
            writer.openBlock("val respHeaders = #T().apply {", RuntimeTypes.Http.HeadersBuilder)
            for ((key, value) in testCase.headers) {
                writer.write("append(#S, #S)", key, value)
            }
            writer.closeBlock("}.build()")
        } else {
            writer.write("val respHeaders = #T.Empty", RuntimeTypes.Http.Headers)
        }

        if (body.isNotBlank()) {
            writer.write("val respBody = #T.fromBytes(#L)", RuntimeTypes.Http.HttpBody, bodyFieldName)
        } else {
            writer.write("val respBody = #T.Empty", RuntimeTypes.Http.HttpBody)
        }

        writer.write("val resp = #T(#T.fromValue(${testCase.code}), respHeaders, respBody)", RuntimeTypes.Http.Response.HttpResponse, RuntimeTypes.Http.StatusCode)
        writer.write("val now = #T.now()", RuntimeTypes.Core.Instant)
        writer.write("#T(request, resp, now, now, callContext())", RuntimeTypes.Http.HttpCall)
        writer.closeBlock("}")

        renderClientConfig()
        writer.write("interceptors.add(interceptor)")
        writer.closeBlock("}")
    }

    private fun isRpcV2Cbor(): Boolean =
        serviceShape.allTraits.keys.any { it.toString().contains("rpcv2Cbor") }

    private fun sanitizeName(id: String): String = id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        .replaceFirstChar { it.lowercaseChar() }
}

private fun Symbol.defaultUnboxedValue(writer: KotlinWriter): String? = when (shape) {
    is LongShape -> "0L"
    is FloatShape -> "0.0f"
    is DoubleShape -> "0.0"
    is NumberShape -> "0"
    is StringShape -> "\"\""
    is BooleanShape -> "false"
    is TimestampShape -> writer.format("#T.now()", RuntimeTypes.Core.Instant)
    is ListShape -> "emptyList()"
    is MapShape -> "emptyMap()"
    else -> null
}
