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
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape

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
open class HttpProtocolClientGenerator(
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val rootNamespace: String,
    protected val features: List<HttpFeature>,
    protected val httpBindingResolver: HttpBindingResolver
) {

    /**
     * Render the implementation of the service client interface
     */
    open fun render(writer: KotlinWriter) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(ctx.service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(ctx.model)

        importSymbols(writer)
        writer.openBlock("class Default${symbol.name}(private val config: ${symbol.name}.Config) : ${symbol.name} {")
            .write("private val client: SdkHttpClient")
            .call { renderInit(writer) }
            .call {
                operations.forEach { op ->
                    renderOperationBody(writer, operationsIndex, op)
                }
            }
            .call { renderClose(writer) }
            .call { renderAdditionalMethods(writer) }
            .closeBlock("}")
            .write("")
    }

    protected open fun importSymbols(writer: KotlinWriter) {
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

    /**
     * Render the class initialization block. By default this configures the HTTP client and installs all the
     * registered features
     */
    protected open fun renderInit(writer: KotlinWriter) {
        writer.openBlock("init {")
            // FIXME - this will eventually come from the client config/builder
            .write("val engineConfig = HttpClientEngineConfig()")
            .write("val httpClientEngine = config.httpClientEngine ?: KtorEngine(engineConfig)")
            .openBlock("client = sdkHttpClient(httpClientEngine) {")
            .call { renderHttpClientConfiguration(writer) }
            .closeBlock("}")
            .closeBlock("}")
    }

    private fun renderHttpClientConfiguration(writer: KotlinWriter) {
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

    /**
     * Render the full operation body (signature, setup, execute)
     */
    protected open fun renderOperationBody(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        val signature = opIndex.operationSignature(ctx.model, ctx.symbolProvider, op)
        writer.openBlock("override \$L {", signature)
            .call { renderOperationSetup(writer, opIndex, op) }
            .call { renderOperationExecute(writer, opIndex, op) }
            .closeBlock("}")
    }

    /**
     * Renders the operation body up to the point where the call is executed. This function is responsbile for setting
     * up the execution context used for this operation
     */
    protected open fun renderOperationSetup(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val httpTrait = httpBindingResolver.httpTrait(op)

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

        // build the execution context
        writer.openBlock("val execCtx = SdkHttpOperation.build {")
            .call {
                if (inputShape.isPresent) {
                    writer.write("serializer = ${op.serializerName()}(input)")
                }

                if (outputShape.isPresent) {
                    writer.write("deserializer = ${op.deserializerName()}()")
                }
                writer.write("expectedHttpStatus = ${httpTrait.code}")
                // property from implementing SdkClient
                writer.write("service = serviceName")
                writer.write("operationName = \$S", op.id.name)
            }
            .closeBlock("}")
    }

    /**
     * Render the actual execution of a request using the HTTP client
     */
    protected open fun renderOperationExecute(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val hasOutputStream = outputShape.map { it.hasStreamingMember(ctx.model) }.orElse(false)
        val httpRequestBuilder = if (!inputShape.isPresent) "builder" else "null"
        if (hasOutputStream) {
            writer.write("return client.execute(execCtx, \$L, block)", httpRequestBuilder)
        } else {
            if (outputShape.isPresent) {
                writer.write("return client.roundTrip(execCtx, \$L)", httpRequestBuilder)
            } else {
                val httpResponseSymbol = Symbol.builder()
                    .name("HttpResponse")
                    .namespace("${KotlinDependency.CLIENT_RT_HTTP.namespace}.response", ".")
                    .addDependency(KotlinDependency.CLIENT_RT_HTTP)
                    .build()
                writer.addImport(httpResponseSymbol)
                // need to not run the response pipeline because there is no valid transform. Explicitly
                // specify the raw (closed) HttpResponse
                writer.write("client.roundTrip<HttpResponse>(execCtx, \$L)", httpRequestBuilder)
            }
        }
    }

    protected open fun renderClose(writer: KotlinWriter) {
        writer.write("")
            // FIXME - this will eventually need to handle the case where an engine is passed in
            .openBlock("override fun close() {")
            .write("client.close()")
            .closeBlock("}")
            .write("")
    }

    /**
     * Render any additional methods to support client operation
     */
    protected open fun renderAdditionalMethods(writer: KotlinWriter) { }
}
