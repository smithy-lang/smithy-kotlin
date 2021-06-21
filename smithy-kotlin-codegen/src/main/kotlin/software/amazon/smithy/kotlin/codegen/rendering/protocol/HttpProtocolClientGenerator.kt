/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.deserializerName
import software.amazon.smithy.kotlin.codegen.rendering.serde.serializerName
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.EndpointTrait

/**
 * Renders an implementation of a service interface for HTTP protocol
 */
abstract class HttpProtocolClientGenerator(
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val middleware: List<ProtocolMiddleware>,
    protected val httpBindingResolver: HttpBindingResolver
) {

    object OperationDeserializer : SectionId {
        const val Operation = "Operation" // Context for operation being codegened at the time of section invocation
    }

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
        middleware.forEach {
            it.renderProperties(writer)
        }
    }

    protected open fun importSymbols(writer: KotlinWriter) {
        writer.addImport("${ctx.settings.pkg.name}.model", "*")
        writer.addImport("${ctx.settings.pkg.name}.transform", "*")

        val defaultClientSymbols = setOf(
            RuntimeTypes.Http.Operation.SdkHttpOperation,
            RuntimeTypes.Http.Operation.context,
            RuntimeTypes.Http.Engine.HttpClientEngineConfig,
            RuntimeTypes.Http.SdkHttpClient,
            RuntimeTypes.Http.SdkHttpClientFn
        )
        writer.addImport(defaultClientSymbols)
        writer.dependencies.addAll(KotlinDependency.HTTP.dependencies)
    }

    //  defaults to Ktor since it's the only available engine in smithy-kotlin runtime
    protected open val defaultHttpClientEngineSymbol: Symbol = buildSymbol {
        name = "KtorEngine"
        namespace(KotlinDependency.HTTP_KTOR_ENGINE)
    }

    /**
     * Render the class initialization block. By default this configures the HTTP client
     */
    protected open fun renderInit(writer: KotlinWriter) {
        writer.addImport(defaultHttpClientEngineSymbol)
        writer.openBlock("init {", "}") {
            writer.write("val httpClientEngine = config.httpClientEngine ?: #T(HttpClientEngineConfig())", defaultHttpClientEngineSymbol)
            writer.write("client = sdkHttpClient(httpClientEngine, manageEngine = config.httpClientEngine == null)")
        }
    }

    /**
     * Render the full operation body (signature, setup, execute)
     */
    protected open fun renderOperationBody(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        writer.renderAnnotations(op)
        val signature = opIndex.operationSignature(ctx.model, ctx.symbolProvider, op)
        writer.openBlock("override #L {", signature)
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
            "val op = SdkHttpOperation.build<#L, #L> {", "}",
            inputSymbolName,
            outputSymbolName
        ) {
            if (inputShape.isPresent) {
                writer.write("serializer = ${op.serializerName()}()")
            } else {
                // no serializer implementation is generated for operations with no input, inline the HTTP
                // protocol request from the operation itself
                // NOTE: this will never be triggered for AWS models where we preprocess operations to always have inputs/outputs
                writer.addImport(RuntimeTypes.Http.Request.HttpRequestBuilder)
                writer.addImport(RuntimeTypes.Core.ExecutionContext)
                writer.openBlock("serializer = object : HttpSerialize<#Q> {", "}", KotlinTypes.Unit) {
                    writer.openBlock("override suspend fun serialize(context: ExecutionContext, input: #Q): HttpRequestBuilder {", "}", KotlinTypes.Unit) {
                        writer.write("val builder = HttpRequestBuilder()")
                        writer.write("builder.method = HttpMethod.#L", httpTrait.method.uppercase())
                        // NOTE: since there is no input the URI can only be a literal (no labels to fill)
                        writer.write("builder.url.path = #S", httpTrait.uri.toString())
                        writer.write("return builder")
                    }
                }
            }

            val deserializerType = if (outputShape.isPresent) {
                "${op.deserializerName()}()"
            } else {
                "UnitDeserializer"
            }

            writer.declareSection(OperationDeserializer, mapOf(OperationDeserializer.Operation to op)) {
                write("deserializer = $deserializerType")
            }

            // execution context
            writer.openBlock("context {", "}") {
                writer.write("expectedHttpStatus = ${httpTrait.code}")
                // property from implementing SdkClient
                writer.write("service = serviceName")
                writer.write("operationName = #S", op.id.name)

                // optional endpoint trait
                op.getTrait<EndpointTrait>()?.let { endpointTrait ->
                    val hostPrefix = endpointTrait.hostPrefix.segments.joinToString(separator = "") { segment ->
                        if (segment.isLabel) {
                            // hostLabel can only target string shapes
                            // see: https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#hostlabel-trait
                            val member = inputShape.get().members().first { member -> member.memberName == segment.content }
                            "\${input.${member.defaultName()}}"
                        } else {
                            segment.content
                        }
                    }
                    writer.write("hostPrefix = #S", hostPrefix)
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
            writer
                .addImport(RuntimeTypes.Http.Operation.execute)
                .write("return op.#T(client, #L, block)", RuntimeTypes.Http.Operation.execute, inputVariableName)
        } else {
            writer.addImport(RuntimeTypes.Http.Operation.roundTrip)
            if (outputShape.isPresent) {
                writer.write("return op.#T(client, #L)", RuntimeTypes.Http.Operation.roundTrip, inputVariableName)
            } else {
                writer.write("op.#T(client, #L)", RuntimeTypes.Http.Operation.roundTrip, inputVariableName)
            }
        }
    }

    protected open fun renderOperationMiddleware(writer: KotlinWriter) {
        writer.openBlock("private fun <I, O>  registerDefaultMiddleware(op: SdkHttpOperation<I,O>){")
            .openBlock("op.apply {")
            .call {
                middleware.forEach { feat ->
                    feat.addImportsAndDependencies(writer)
                    if (feat.needsConfiguration) {
                        writer.openBlock("install(#L) {", feat.name)
                            .call { feat.renderConfigure(writer) }
                            .closeBlock("}")
                    } else {
                        writer.write("install(#L)", feat.name)
                    }
                }
            }
            .closeBlock("}")
            .closeBlock("}")
    }

    protected open fun renderClose(writer: KotlinWriter) {
        writer.write("")
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
