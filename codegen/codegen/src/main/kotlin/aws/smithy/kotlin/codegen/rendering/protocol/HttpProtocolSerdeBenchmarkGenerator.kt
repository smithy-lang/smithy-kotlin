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
 * Generates JMH benchmark classes for protocol test cases tagged with "serde-benchmark".
 * Follows the same pattern as the endpoint resolution benchmarks in aws-sdk-kotlin.
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
        writer.write("@State(Scope.Benchmark)")
        writer.write("@BenchmarkMode(Mode.AverageTime)")
        writer.write("@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)")
        writer.write("@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)")
        writer.write("@Measurement(iterations = 20, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)")
        writer.openBlock("class $className {")

        // Use TestEngine which returns HTTP 200 by default — the request will be serialized
        // and sent to the engine. We catch any exception from the operation (e.g. deserialization
        // of the empty response may fail) since we only care about measuring serialization.
        writer.write("")
        writer.openBlock("private val client = ${serviceSymbol.name} {")
        writer.write("httpClient = #T()", RuntimeTypes.HttpTest.TestEngine)
        renderClientConfig()
        writer.closeBlock("}")

        for (testCase in testCases) {
            renderRequestBenchmark(testCase)
        }

        writer.closeBlock("}")
    }

    fun renderResponseBenchmarkClass(className: String, testCases: List<HttpResponseTestCase>) {
        writeImports()

        writer.write("")
        writer.write("@State(Scope.Benchmark)")
        writer.write("@BenchmarkMode(Mode.AverageTime)")
        writer.write("@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)")
        writer.write("@Warmup(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)")
        writer.write("@Measurement(iterations = 20, time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)")
        writer.openBlock("class $className {")

        for (testCase in testCases) {
            renderResponseClientField(testCase)
        }

        writer.write("")

        for (testCase in testCases) {
            renderResponseBenchmark(testCase)
        }

        writer.closeBlock("}")
    }

    private fun writeImports() {
        writer.write("import kotlinx.benchmark.*")
        writer.write("import kotlinx.coroutines.runBlocking")
        writer.write("import org.openjdk.jmh.annotations.State")
    }

    private fun renderClientConfig() {
        // Per spec: explicit region, endpoint, and mock credentials
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

    private fun renderRequestBenchmark(testCase: HttpRequestTestCase) {
        val methodName = sanitizeName(testCase.id)

        testCase.documentation.ifPresent { writer.dokka(it) }
        writer.write("")
        writer.write("@Benchmark")
        writer.openBlock("fun $methodName() = runBlocking {")

        if (operation.input.isPresent) {
            val inputShape = model.expectShape<StructureShape>(operation.input.get())
            writer.writeInline("val input = ")
                .indent()
                .call {
                    ShapeValueGenerator(model, symbolProvider, explicitReceiver = true)
                        .instantiateShapeInline(writer, inputShape, testCase.params)
                }
                .dedent()
                .write("")
            writer.openBlock("try {")
            writer.write("client.#L(input)", opName)
            writer.closeBlock("} catch (_: Exception) {}")
        } else {
            writer.openBlock("try {")
            writer.write("client.#L()", opName)
            writer.closeBlock("} catch (_: Exception) {}")
        }

        writer.closeBlock("}")
    }

    private fun renderResponseClientField(testCase: HttpResponseTestCase) {
        val fieldName = "client_${sanitizeName(testCase.id)}"
        val bodyFieldName = "respBody_${sanitizeName(testCase.id)}"

        // Pre-compute response body bytes once (not per benchmark invocation)
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

        // Headers
        if (testCase.headers.isNotEmpty()) {
            writer.openBlock("val respHeaders = #T().apply {", RuntimeTypes.Http.HeadersBuilder)
            for ((key, value) in testCase.headers) {
                writer.write("append(#S, #S)", key, value)
            }
            writer.closeBlock("}.build()")
        } else {
            writer.write("val respHeaders = #T.Empty", RuntimeTypes.Http.Headers)
        }

        // Body - reference pre-computed bytes
        if (body.isNotBlank()) {
            writer.write("val respBody = #T.fromBytes(#L)", RuntimeTypes.Http.HttpBody, bodyFieldName)
        } else {
            writer.write("val respBody = #T.Empty", RuntimeTypes.Http.HttpBody)
        }

        writer.write("val resp = #T(#T.fromValue(${testCase.code}), respHeaders, respBody)", RuntimeTypes.Http.Response.HttpResponse, RuntimeTypes.Http.StatusCode)
        writer.write("val now = #T.now()", RuntimeTypes.Core.Instant)
        writer.write("#T(request, resp, now, now)", RuntimeTypes.Http.HttpCall)
        writer.closeBlock("}")

        renderClientConfig()
        writer.closeBlock("}")
    }

    private fun renderResponseBenchmark(testCase: HttpResponseTestCase) {
        val methodName = sanitizeName(testCase.id)
        val fieldName = "client_$methodName"

        testCase.documentation.ifPresent { writer.dokka(it) }
        writer.write("")
        writer.write("@Benchmark")
        writer.openBlock("fun $methodName() = runBlocking {")

        if (operation.input.isPresent) {
            val inputShape = model.expectShape<StructureShape>(operation.input.get())
            val inputSymbol = symbolProvider.toSymbol(inputShape)
            // Populate required members with dummy values so the client doesn't reject the request
            val requiredMembers = inputShape.members().filter { it.isRequired }
            writer.openBlock("val input = #T {", inputSymbol)
            requiredMembers.forEach { member ->
                val memberSymbol = symbolProvider.toSymbol(member)
                val defaultValue = memberSymbol.defaultUnboxedValue(writer)
                if (defaultValue != null) {
                    writer.write("#L = #L", member.defaultName(), defaultValue)
                }
            }
            writer.closeBlock("}")
            writer.write("$fieldName.#L(input)", opName)
        } else {
            writer.write("$fieldName.#L()", opName)
        }

        writer.closeBlock("}")
    }

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
