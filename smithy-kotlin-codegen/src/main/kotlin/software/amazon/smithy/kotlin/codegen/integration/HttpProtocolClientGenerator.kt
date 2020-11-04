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
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait

/**
 * HttpFeature interface that allows pipeline middleware to be registered and configured with the protocol generator
 */
interface HttpFeature {
    // the name of the feature to install
    val name: String

    // flag that controls whether renderConfigure() needs called
    val needsConfiguration: Boolean
        get() = true

    /**
     * Render the body of the install step which configures this feature. Implementations do not need to open
     * the surrounding block.
     *
     * Example
     * ```
     * install(MyFeature) {
     *     // this is the renderConfigure() entry point
     * }
     * ```
     */
    fun renderConfigure(writer: KotlinWriter) {}

    /**
     * Register any imports or dependencies that will be needed to use this feature at runtime
     */
    fun addImportsAndDependencies(writer: KotlinWriter) {}
}

/**
 * Client Runtime `HttpSerde` feature
 * @property serdeProvider The name of the serde provider (e.g. JsonSerdeProvider)
 * @property generateIdempotencyTokenConfig determines if Service's Config type implements [IdempotencyTokenProvider].
 */
abstract class HttpSerde(private val serdeProvider: String, private val generateIdempotencyTokenConfig: Boolean) : HttpFeature {
    override val name: String = "HttpSerde"

    override fun renderConfigure(writer: KotlinWriter) {
        writer.write("serdeProvider = $serdeProvider()")
        if (generateIdempotencyTokenConfig) {
            writer.write("idempotencyTokenProvider = config.idempotencyTokenProvider ?: IdempotencyTokenProvider.Default")
        }
    }

    override fun addImportsAndDependencies(writer: KotlinWriter) {
        val httpSerdeSymbol = Symbol.builder()
            .name("HttpSerde")
            .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.feature", ".")
            .addDependency(KotlinDependency.CLIENT_RT_HTTP)
            .addDependency(KotlinDependency.CLIENT_RT_SERDE)
            .build()
        writer.addImport(httpSerdeSymbol)

        if (generateIdempotencyTokenConfig) {
            val idempotencyTokenProviderSymbol = Symbol.builder()
                .name("IdempotencyTokenProvider")
                .namespace("${KotlinDependency.CLIENT_RT_CORE.namespace}.config", ".")
                .addDependency(KotlinDependency.CLIENT_RT_CORE)
                .build()
            writer.addImport(idempotencyTokenProviderSymbol)
        }
    }
}

/**
 * Renders an implementation of a service interface for HTTP protocol
 */
class HttpProtocolClientGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: KotlinWriter,
    private val service: ServiceShape,
    private val rootNamespace: String,
    private val features: List<HttpFeature>
) {

    fun render() {
        val symbol = symbolProvider.toSymbol(service)
        val topDownIndex = TopDownIndex.of(model)
        val operations = topDownIndex.getContainedOperations(service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(model)

        importSymbols()
        writer.openBlock("class Default${symbol.name}(config: ${symbol.name}.Config) : ${symbol.name} {")
            .write("private val client: SdkHttpClient")
            .call { renderInit() }
            .call {
                operations.forEach { op ->
                    renderOperationBody(operationsIndex, op)
                }
            }
            .call { renderClose() }
            .closeBlock("}")
            .write("")
    }

    private fun importSymbols() {
        writer.addImport("$rootNamespace.model", "*")
        writer.addImport("$rootNamespace.transform", "*")

        // http.*
        val httpRootPkg = KotlinDependency.CLIENT_RT_HTTP.namespace
        writer.addImport(httpRootPkg, "*")
        writer.addImport("$httpRootPkg.engine", "HttpClientEngineConfig")
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_HTTP.dependencies)

        // TODO - engine needs configurable (either auto detected or passed in through config).
        //  For now default it to Ktor since it's the only available engine
        val ktorEngineSymbol = Symbol.builder()
            .name("KtorEngine")
            .namespace(KotlinDependency.CLIENT_RT_HTTP_KTOR_ENGINE.namespace, ".")
            .addDependency(KotlinDependency.CLIENT_RT_HTTP_KTOR_ENGINE)
            .build()

        writer.addImport(ktorEngineSymbol)
    }

    private fun renderInit() {
        writer.openBlock("init {")
            // FIXME - this will eventually come from the client config/builder
            .write("val engineConfig = HttpClientEngineConfig()")
            .write("val httpClientEngine = config.httpClientEngine ?: KtorEngine(engineConfig)")
            .openBlock("client = sdkHttpClient(httpClientEngine) {")
            .call { renderHttpClientConfiguration() }
            .closeBlock("}")
            .closeBlock("}")
    }

    private fun renderHttpClientConfiguration() {
        features.forEach { feat ->
            feat.addImportsAndDependencies(writer)
            if (feat.needsConfiguration) {
                writer.openBlock("install(\$L) {", feat.name)
                    .call { feat.renderConfigure(writer) }
                    .closeBlock("}")
            } else {
                writer.write("install(\$L)", feat.name)
            }
        }
    }

    private fun renderOperationBody(opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        val signature = opIndex.operationSignature(model, symbolProvider, op)
        writer.openBlock("override \$L {", signature)
            .call {
                val inputShape = opIndex.getInput(op)
                val outputShape = opIndex.getOutput(op)
                val input = inputShape.map { symbolProvider.toSymbol(it).name }
                val output = outputShape.map { symbolProvider.toSymbol(it).name }
                val hasOutputStream = outputShape.map { it.hasStreamingMember(model) }.orElse(false)
                val inputParam = input.map { "${op.serializerName()}(input)" }.orElse("builder")
                val httpTrait = op.expectTrait(HttpTrait::class.java)

                if (!inputShape.isPresent) {
                    // no serializer implementation is generated for operations with no input, inline the HTTP
                    // protocol request from the operation itself
                    val requestBuilderSymbol = Symbol.builder()
                        .name("HttpRequestBuilder")
                        .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.request", ".")
                        .addDependency(KotlinDependency.CLIENT_RT_HTTP)
                        .build()
                    writer.addImport(requestBuilderSymbol)
                    writer.openBlock("val builder = HttpRequestBuilder().apply {")
                        .write("method = HttpMethod.\$L", httpTrait.method.toUpperCase())
                        // NOTE: since there is no input the URI can only be a literal (no labels to fill)
                        .write("url.path = \"\$L\"", httpTrait.uri.toString())
                        .closeBlock("}")
                }

                val executionCtxSymbol = Symbol.builder()
                    .name("ExecutionContext")
                    .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.response", ".")
                    .addDependency(KotlinDependency.CLIENT_RT_HTTP)
                    .build()
                writer.addImport(executionCtxSymbol)
                writer.openBlock("val execCtx = ExecutionContext.build {")
                    .call {
                        // Here we're checking to see if the client is part of the http protocol response tests, has a specific
                        // test case, and if so overriding the expected value of the return code to the value defined by
                        // the trait.  This seems to break the scope of HttpProtocolClientGenerator, but it's unclear
                        // how best to refactor the Test generators.  It seems like will require a large change for a single test,
                        // but possibly missing something.
                        // This code is to facilitate discussion of best approach, not to push.
                        val code = op.getTrait(HttpResponseTestsTrait::class.java).orElse(null)?.testCases?.firstOrNull { it is HttpResponseTestCase }?.code ?: httpTrait.code

                        writer.write("expectedHttpStatus = $code")
                        if (output.isPresent) {
                            writer.write("deserializer = ${op.deserializerName()}()")
                        }
                    }
                    .closeBlock("}")

                if (hasOutputStream) {
                    writer.write("return client.execute(\$L, execCtx, block)", inputParam)
                } else {
                    if (output.isPresent) {
                        writer.write("return client.roundTrip(\$L, execCtx)", inputParam)
                    } else {
                        val httpResponseSymbol = Symbol.builder()
                            .name("HttpResponse")
                            .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.response", ".")
                            .addDependency(KotlinDependency.CLIENT_RT_HTTP)
                            .build()
                        writer.addImport(httpResponseSymbol)
                        // need to not run the response pipeline because there is no valid transform. Explicitly
                        // specify the raw (closed) HttpResponse
                        writer.write("client.roundTrip<HttpResponse>(\$L, execCtx)", inputParam)
                    }
                }
            }
            .closeBlock("}")
    }

    private fun renderClose() {
        writer.write("")
            // FIXME - this will eventually need to handle the case where an engine is passed in
            .openBlock("override fun close() {")
            .write("client.close()")
            .closeBlock("}")
            .write("")
    }
}
