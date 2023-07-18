/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.aws.traits.HttpChecksumTrait
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.knowledge.AuthIndex
import software.amazon.smithy.kotlin.codegen.model.operationSignature
import software.amazon.smithy.kotlin.codegen.rendering.auth.AuthSchemeProviderAdapterGenerator
import software.amazon.smithy.kotlin.codegen.rendering.auth.IdentityProviderConfigGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointResolverAdapterGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.deserializerName
import software.amazon.smithy.kotlin.codegen.rendering.serde.serializerName
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.EndpointTrait
import software.amazon.smithy.model.traits.HttpChecksumRequiredTrait

/**
 * Renders an implementation of a service interface for HTTP protocol
 */
abstract class HttpProtocolClientGenerator(
    protected val ctx: ProtocolGenerator.GenerationContext,
    protected val middleware: List<ProtocolMiddleware>,
    protected val httpBindingResolver: HttpBindingResolver,
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

        writer.openBlock("internal class Default${symbol.name}(override val config: ${symbol.name}.Config) : ${symbol.name} {")
            .call { renderProperties(writer) }
            .write("")
            .call { renderInit(writer) }
            .write("")
            .call {
                // allow middleware to write properties that can be re-used
                val appliedMiddleware = mutableSetOf<ProtocolMiddleware>()
                operations.forEach { op ->
                    middleware.filterTo(appliedMiddleware) { it.isEnabledFor(ctx, op) }
                }

                // render properties from middleware to service client
                appliedMiddleware.forEach { it.renderProperties(writer) }
            }
            .call {
                operations.forEach { op ->
                    renderOperationBody(writer, operationsIndex, op)
                }
            }
            .write("")
            .call { renderClose(writer) }
            .write("")
            .call { renderAdditionalMethods(writer) }
            .closeBlock("}")
            .write("")
    }

    /**
     * Render any properties this class should have.
     */
    protected open fun renderProperties(writer: KotlinWriter) {
        writer.write("private val managedResources = #T()", RuntimeTypes.Core.IO.SdkManagedGroup)
        writer.write("private val client = #T(config.httpClient)", RuntimeTypes.HttpClient.SdkHttpClient)

        // render auth resolver related properties
        writer.write("private val identityProviderConfig = #T(config)", IdentityProviderConfigGenerator.getSymbol(ctx.settings))

        // FIXME - we probably need a way for auth handlers to signal that they are configured (e.g. config properties are not null). Right now this assumes
        // they are all configured but a service may support multiple auth schemes and a client may not need to configure all of them
        writer.withBlock(
            "private val configuredAuthSchemes = with(config.authSchemes.associateBy(#T::schemeId).toMutableMap()){",
            "}",
            RuntimeTypes.Auth.HttpAuth.HttpAuthScheme,
        ) {
            val authIndex = AuthIndex()
            val allAuthHandlers = authIndex.authHandlersForService(ctx)

            allAuthHandlers.forEach {
                val (format, args) = if (it.authSchemeIdSymbol != null) {
                    "#T" to arrayOf(it.authSchemeIdSymbol!!)
                } else {
                    "#T(#S)" to arrayOf(RuntimeTypes.Auth.Identity.AuthSchemeId, it.authSchemeId)
                }

                withBlock(
                    "getOrPut($format){",
                    "}",
                    *args,
                ) {
                    it.instantiateAuthSchemeExpr(ctx, this)
                }
            }

            write("toMap()")
        }
    }

    protected open fun importSymbols(writer: KotlinWriter) {
        writer.addImport("${ctx.settings.pkg.name}.model", "*")
        writer.addImport("${ctx.settings.pkg.name}.transform", "*")

        val defaultClientSymbols = setOf(
            RuntimeTypes.HttpClient.Operation.SdkHttpOperation,
            RuntimeTypes.HttpClient.Operation.context,
        )
        writer.addImport(defaultClientSymbols)
        writer.dependencies.addAll(KotlinDependency.HTTP.dependencies)
    }

    /**
     * Render the class initialization block.
     */
    protected open fun renderInit(writer: KotlinWriter) {
        writer.withBlock("init {", "}") {
            write("managedResources.#T(config.httpClient)", RuntimeTypes.Core.IO.addIfManaged)
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
            .call { renderOperationMiddleware(op, writer) }
            .call { renderFinalizeBeforeExecute(writer, opIndex, op) }
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

        val (inputSymbolName, outputSymbolName) = ioSymbolNames(op)

        writer.openBlock(
            "val op = SdkHttpOperation.build<#L, #L> {",
            "}",
            inputSymbolName,
            outputSymbolName,
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
                    writer.openBlock(
                        "override suspend fun serialize(context: ExecutionContext, input: #Q): HttpRequestBuilder {",
                        "}",
                        KotlinTypes.Unit,
                    ) {
                        writer.write("val builder = HttpRequestBuilder()")
                        writer.write("builder.method = HttpMethod.#L", httpTrait.method.uppercase())
                        // NOTE: since there is no input the URI can only be a literal (no labels to fill)
                        writer.write("builder.url.path = #S", httpTrait.uri.toString())
                        writer.write("return builder")
                    }
                }
            }

            if (outputShape.isPresent) {
                writer.write("deserializer = ${op.deserializerName()}()")
            } else {
                writer.write("deserializer = UnitDeserializer")
            }

            // execution context
            writer.openBlock("context {", "}") {
                writer.write("expectedHttpStatus = ${httpTrait.code}")
                // property from implementing SdkClient
                writer.write("operationName = #S", op.id.name)

                // optional endpoint trait
                op.getTrait<EndpointTrait>()?.let { endpointTrait ->
                    val hostPrefix = endpointTrait.hostPrefix.segments.joinToString(separator = "") { segment ->
                        if (segment.isLabel) {
                            // hostLabel can only target string shapes
                            // see: https://awslabs.github.io/smithy/1.0/spec/core/endpoint-traits.html#hostlabel-trait
                            val member =
                                inputShape.get().members().first { member -> member.memberName == segment.content }
                            "\${input.${member.defaultName()}}"
                        } else {
                            segment.content
                        }
                    }
                    writer.write("hostPrefix = #S", hostPrefix)
                }
            }

            writer.write(
                "execution.auth = #T(#T, configuredAuthSchemes, identityProviderConfig)",
                RuntimeTypes.HttpClient.Operation.OperationAuthConfig,
                AuthSchemeProviderAdapterGenerator.getSymbol(ctx.settings),
            )

            writer.write("execution.endpointResolver = #T(config)", EndpointResolverAdapterGenerator.getSymbol(ctx.settings))
            writer.write("execution.retryStrategy = config.retryStrategy")
        }
    }

    protected open fun renderFinalizeBeforeExecute(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        // add config interceptors last (after all middleware and SDK defaults have had a chance to register)
        writer.write("op.interceptors.addAll(config.interceptors)")
    }

    /**
     * Render the actual execution of a request using the HTTP client
     */
    protected open fun renderOperationExecute(writer: KotlinWriter, opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val hasOutputStream = outputShape.map { it.hasStreamingMember(ctx.model) }.orElse(false)
        val inputVariableName = if (inputShape.isPresent) "input" else KotlinTypes.Unit.fullName

        writer
            .write(
                """val rootSpan = config.tracer.createRootSpan("#L-${'$'}{op.context.#T}")""",
                op.id.name,
                RuntimeTypes.HttpClient.Operation.sdkRequestId,
            )
            .withBlock(
                "return #T.#T(rootSpan) {",
                "}",
                RuntimeTypes.KotlinCoroutines.coroutineContext,
                RuntimeTypes.Tracing.Core.withRootTraceSpan,
            ) {
                if (hasOutputStream) {
                    write("op.#T(client, #L, block)", RuntimeTypes.HttpClient.Operation.execute, inputVariableName)
                } else {
                    write("op.#T(client, #L)", RuntimeTypes.HttpClient.Operation.roundTrip, inputVariableName)
                }
            }
    }

    private fun ioSymbolNames(op: OperationShape): Pair<String, String> {
        val opIndex = OperationIndex.of(ctx.model)
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)

        val inputSymbolName =
            inputShape.map { ctx.symbolProvider.toSymbol(it).name }.getOrNull() ?: KotlinTypes.Unit.fullName
        val outputSymbolName =
            outputShape.map { ctx.symbolProvider.toSymbol(it).name }.getOrNull() ?: KotlinTypes.Unit.fullName

        return Pair(inputSymbolName, outputSymbolName)
    }

    /**
     * Renders the operation specific middleware
     *
     * Example:
     * ```
     * op.install(<Middleware>)
     * ```
     */
    protected open fun renderOperationMiddleware(op: OperationShape, writer: KotlinWriter) {
        middleware
            .filter { it.isEnabledFor(ctx, op) }
            .sortedBy(ProtocolMiddleware::order)
            .forEach { middleware ->
                middleware.render(ctx, op, writer)
            }

        op.renderIsMd5ChecksumRequired(writer)
    }

    /**
     * Render the client close implementation, the base behavior of which is to close any managed config resources.
     */
    protected open fun renderClose(writer: KotlinWriter) {
        writer.withBlock("override fun close() {", "}") {
            write("managedResources.unshareAll()")
        }
    }

    /**
     * Render any additional methods to support client operation
     */
    protected open fun renderAdditionalMethods(writer: KotlinWriter) { }

    /**
     * Render optionally installing Md5ChecksumMiddleware.
     * The Md5 middleware will only be installed if the operation requires a checksum and the user has not opted-in to flexible checksums.
     */
    private fun OperationShape.renderIsMd5ChecksumRequired(writer: KotlinWriter) {
        val httpChecksumTrait = getTrait<HttpChecksumTrait>()

        // the checksum requirement can be modeled in either HttpChecksumTrait's `requestChecksumRequired` or the HttpChecksumRequired trait
        if (!hasTrait<HttpChecksumRequiredTrait>() && httpChecksumTrait == null) {
            return
        }

        val requestAlgorithmMember = ctx.model.getShape(input.get()).getOrNull()
            ?.members()
            ?.firstOrNull { it.memberName == httpChecksumTrait?.requestAlgorithmMember?.getOrNull() }

        if (hasTrait<HttpChecksumRequiredTrait>() || httpChecksumTrait?.isRequestChecksumRequired == true) {
            val interceptorSymbol = RuntimeTypes.HttpClient.Interceptors.Md5ChecksumInterceptor
            val inputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape(inputShape))

            requestAlgorithmMember?.let {
                writer.withBlock("op.interceptors.add(#T<#T> { ", "})", interceptorSymbol, inputSymbol) {
                    writer.write("it.#L?.value == null", requestAlgorithmMember.defaultName())
                }
            } ?: writer.write("op.interceptors.add(#T<#T>())", interceptorSymbol, inputSymbol)
        }
    }
}
