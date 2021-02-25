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
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.EndpointTrait

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
     * Register any imports or dependencies that will be needed to use this feature at runtime
     */
    fun addImportsAndDependencies(writer: KotlinWriter) {}

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
     * Render any instance properties (e.g. add private properties that exist for the lifetime of the client
     * that are re-used by the feature)
     */
    fun renderProperties(writer: KotlinWriter) {}
}

/**
 * Renders an implementation of a service interface for HTTP protocol
 */
abstract class HttpProtocolClientGenerator(
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val features: List<HttpFeature>,
    protected val httpBindingResolver: HttpBindingResolver
) {

    /**
     * The serialization/deserialization provider to be used for serialize/deserialize
     * See: https://github.com/awslabs/smithy-kotlin/blob/main/client-runtime/serde/common/src/software/aws/clientrt/serde/SerdeProvider.kt
     */
    abstract val serdeProviderSymbol: Symbol

    /**
     * Render the implementation of the service client interface
     */
    open fun render(writer: KotlinWriter) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(ctx.service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(ctx.model)

        importSymbols(writer)
        writer.openBlock("internal class Default${symbol.name}(private val config: ${symbol.name}.Config) : ${symbol.name} {")
            .call { renderProperties(writer) }
            .call { renderInit(writer) }
            .call {
                operations.forEach { op ->
                    renderOperationBody(writer, operationsIndex, op)
                }
            }
            .call { renderClose(writer) }
            .call { renderOperationMiddleware(writer) }
            .call { renderAdditionalMethods(writer) }
            .closeBlock("}")
            .write("")
    }

    /**
     * Render any properties this class should have.
     */
    protected open fun renderProperties(writer: KotlinWriter) {
        writer.write("private val client: SdkHttpClient")
        writer.write("private val serde: SerdeProvider = ${serdeProviderSymbol.name}()")
        features.forEach {
            it.renderProperties(writer)
        }
    }

    protected open fun importSymbols(writer: KotlinWriter) {
        val rootNamespace = ctx.settings.moduleName
        writer.addImport("$rootNamespace.model", "*")
        writer.addImport("$rootNamespace.transform", "*")

        // http.*
        val httpRootPkg = KotlinDependency.CLIENT_RT_HTTP.namespace
        writer.addImport(httpRootPkg, "*")
        writer.addImport("$httpRootPkg.operation", "*")
        writer.addImport("$httpRootPkg.engine", "HttpClientEngineConfig")
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_HTTP.dependencies)

        // serialization
        writer.addImport(serdeProviderSymbol)
        writer.addImport("SerdeProvider", KotlinDependency.CLIENT_RT_SERDE)

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
        writer.openBlock("init {", "}") {
            // FIXME - should the generated service client have httpClientEngineConfig on it? If you pass in an engine it's a conflicting property
            // as it'll never be consumed...
            writer.write("val engineConfig = config.httpClientEngineConfig ?: HttpClientEngineConfig()")
            writer.write("val httpClientEngine = config.httpClientEngine ?: KtorEngine(engineConfig)")
            writer.write("client = sdkHttpClient(httpClientEngine)")
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
     * Renders the operation body up to the point where the call is executed. This function is responsible for setting
     * up the execution context used for this operation
     */
    protected open fun renderOperationSetup(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val httpTrait = httpBindingResolver.httpTrait(op)

        val inputSymbolName = inputShape.map { ctx.symbolProvider.toSymbol(it).name }.getOrNull() ?: KotlinTypes.Unit.fullName
        val outputSymbolName = outputShape.map { ctx.symbolProvider.toSymbol(it).name }.getOrNull() ?: KotlinTypes.Unit.fullName

        writer.openBlock(
            "val op = SdkHttpOperation.build<\$L, \$L> {", "}",
            inputSymbolName,
            outputSymbolName
        ) {
            if (inputShape.isPresent) {
                writer.write("serializer = ${op.serializerName()}(serde::serializer)")
            } else {
                // no serializer implementation is generated for operations with no input, inline the HTTP
                // protocol request from the operation itself
                // FIXME - this goes away when we implement model evolution and generate input/output types regardless of whether the model has them
                writer.addImport("HttpRequestBuilder", KotlinDependency.CLIENT_RT_HTTP, subpackage = "request")
                writer.addImport("ExecutionContext", KotlinDependency.CLIENT_RT_CORE, subpackage = "client")
                writer.openBlock("serializer = object : HttpSerialize<\$Q> {", "}", KotlinTypes.Unit) {
                    writer.openBlock("override suspend fun serialize(context: ExecutionContext, input: \$Q): HttpRequestBuilder {", "}", KotlinTypes.Unit) {
                        writer.write("val builder = HttpRequestBuilder()")
                        writer.write("builder.method = HttpMethod.\$L", httpTrait.method.toUpperCase())
                        // NOTE: since there is no input the URI can only be a literal (no labels to fill)
                        writer.write("builder.url.path = \$S", httpTrait.uri.toString())
                        writer.write("return builder")
                    }
                }
            }

            if (outputShape.isPresent) {
                writer.write("deserializer = ${op.deserializerName()}(serde::deserializer)")
            } else {
                writer.write("deserializer = UnitDeserializer")
            }

            // execution context
            writer.openBlock("context {", "}") {
                writer.write("expectedHttpStatus = ${httpTrait.code}")
                // property from implementing SdkClient
                writer.write("service = serviceName")
                writer.write("operationName = \$S", op.id.name)

                // optional endpoint trait
                op.getTrait<EndpointTrait>()?.let {
                    val hostPrefix = it.hostPrefix.segments.joinToString(separator = "") { segment ->
                        if (segment.isLabel) {
                            // hostLabel can only target string shapes
                            // see: https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#hostlabel-trait
                            val member = inputShape.get().members().first { it.memberName == segment.content }
                            "\${input.${member.defaultName()}}"
                        } else {
                            segment.content
                        }
                    }
                    writer.write("hostPrefix = \$S", hostPrefix)
                }
            }
        }

        writer.write("registerDefaultMiddleware(op)")
    }

    /**
     * Render the actual execution of a request using the HTTP client
     */
    protected open fun renderOperationExecute(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val hasOutputStream = outputShape.map { it.hasStreamingMember(ctx.model) }.orElse(false)
        val inputVariableName = if (inputShape.isPresent) "input" else KotlinTypes.Unit.fullName

        if (hasOutputStream) {
            writer.write("return op.execute(client, \$L, block)", inputVariableName)
        } else {
            if (outputShape.isPresent) {
                writer.write("return op.roundTrip(client, \$L)", inputVariableName)
            } else {
                writer.write("op.roundTrip(client, \$L)", inputVariableName)
            }
        }
    }

    protected open fun renderOperationMiddleware(writer: KotlinWriter) {
        writer.openBlock("private fun <I, O>  registerDefaultMiddleware(op: SdkHttpOperation<I,O>){")
            .openBlock("op.apply {")
            .call {
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
            .closeBlock("}")
            .closeBlock("}")
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
